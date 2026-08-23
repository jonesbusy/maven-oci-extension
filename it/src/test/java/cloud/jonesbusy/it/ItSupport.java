package cloud.jonesbusy.it;

import org.apache.maven.shared.invoker.DefaultInvocationRequest;
import org.apache.maven.shared.invoker.DefaultInvoker;
import org.apache.maven.shared.invoker.InvocationRequest;
import org.apache.maven.shared.invoker.InvocationResult;
import org.apache.maven.shared.invoker.Invoker;
import org.apache.maven.shared.invoker.MavenInvocationException;
import org.junit.jupiter.api.BeforeAll;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Shared harness for the Maven-process-level integration tests: locates a Maven 4.x executable,
 * installs oci-extension-common/oci-extension-core into the local repository once per test run
 * (child processes resolve them as a real dependency/extension, not via in-reactor visibility), and
 * provides helpers to copy a template project and run a Maven goal against it.
 */
abstract class ItSupport {

    static File mavenExecutable;

    @BeforeAll
    static void locateMavenAndInstallReactor() throws MavenInvocationException, IOException {
        mavenExecutable = Maven4Locator.findMavenExecutable().orElse(null);
        assumeTrue(mavenExecutable != null, "No Maven 4.x executable found (set OCI_IT_MAVEN_HOME to one)");
        installReactorArtifactsOnce();
    }

    /**
     * Test classes run concurrently (confirmed empirically: a plain {@code synchronized} guard on a
     * shared static field did not serialize them, yet a same-path {@link FileChannel#lock()} throws
     * {@link OverlappingFileLockException} instead of the "blocks across processes" behavior its
     * javadoc describes -- meaning failsafe's JUnit Platform executor gives each test class its own
     * classloader, so {@code ItSupport}'s statics are not actually shared, while the JVM-wide,
     * classloader-agnostic file-lock table still sees the overlap). So: an OS file lock is still the
     * right primitive, but a caller that hits the "already locked in this JVM" exception must retry
     * rather than treat it as real contention.
     * <p>
     * A marker file (under {@code it/target/}, so it can't outlive a {@code mvn clean} and go stale
     * across unrelated runs) is what actually prevents redundant re-installs, not just the lock: a
     * second class's install running concurrently with a first class's already-running deploy/resolve
     * test was observed to transiently delete/recreate the very artifact the first class was reading
     * mid-test, which "no lock at all" and "lock but always reinstall" both still allow.
     */
    private static void installReactorArtifactsOnce() throws MavenInvocationException, IOException {
        Path lockFile = Path.of(System.getProperty("java.io.tmpdir"), "oci-extension-it-install.lock");
        Path markerFile = Path.of("target", "it-install.marker");

        try (FileChannel channel = FileChannel.open(
                lockFile, StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.READ)) {
            FileLock lock = acquireLockWithRetry(channel);
            try {
                if (!Files.isRegularFile(markerFile)) {
                    installReactorArtifacts();
                    Files.createDirectories(markerFile.getParent());
                    Files.writeString(markerFile, "installed");
                }
            } finally {
                lock.release();
            }
        }
    }

    private static FileLock acquireLockWithRetry(FileChannel channel) throws IOException {
        while (true) {
            try {
                return channel.lock();
            } catch (OverlappingFileLockException e) {
                try {
                    Thread.sleep(200);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new IOException("Interrupted while waiting for the IT install lock", interrupted);
                }
            }
        }
    }

    private static void installReactorArtifacts() throws MavenInvocationException {
        File repoRoot = new File("..").getAbsoluteFile();

        InvocationRequest request = new DefaultInvocationRequest();
        request.setBaseDirectory(repoRoot);
        request.setGoals(List.of("install"));
        // Exclude "it" itself (would otherwise recurse into these very integration tests); this
        // still installs the aggregator/parent pom, which oci-extension-core's <parent> needs to
        // resolve, in addition to oci-extension-common and oci-extension-core.
        request.setProjects(List.of("!it"));
        Properties properties = new Properties();
        properties.setProperty("skipTests", "true");
        request.setProperties(properties);
        request.setBatchMode(true);

        ItResult result = invoke(request);
        if (result.exitCode() != 0) {
            throw new IllegalStateException(
                    "Failed to install oci-extension-common/oci-extension-core for IT setup (exit="
                            + result.exitCode() + "):\n" + result.output());
        }
    }

    /**
     * Copies the template project at {@code it/src/test/resources/it/<name>} into {@code targetDir}.
     */
    static Path copyTemplate(String name, Path targetDir) throws IOException {
        Path source = Path.of("src/test/resources/it", name);
        if (!Files.isDirectory(source)) {
            throw new IllegalArgumentException("No such IT template: " + source.toAbsolutePath());
        }
        try (var paths = Files.walk(source)) {
            for (Path path : (Iterable<Path>) paths::iterator) {
                Path relative = source.relativize(path);
                Path target = targetDir.resolve(relative.toString());
                if (Files.isDirectory(path)) {
                    Files.createDirectories(target);
                } else {
                    Files.createDirectories(target.getParent());
                    Files.copy(path, target, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
        return targetDir;
    }

    /** Replaces every occurrence of {@code token} with {@code value} in {@code file}, in place. */
    static void replaceToken(Path file, String token, String value) throws IOException {
        String content = Files.readString(file);
        Files.writeString(file, content.replace(token, value));
    }

    /**
     * Deletes {@code groupId:artifactId:version} from the default local repository. Tests use this
     * between a deploy step and a resolve step to prove the resolve genuinely fetches from the
     * registry, without resorting to a separate/empty local repository for the resolve step -- which
     * would also make oci-extension-core itself unresolvable there, since it only exists as a
     * SNAPSHOT in the default repository this harness installed it into.
     */
    static void deleteFromDefaultLocalRepository(String groupId, String artifactId, String version) throws IOException {
        Path artifactDir = Path.of(System.getProperty("user.home"), ".m2", "repository")
                .resolve(groupId.replace('.', '/'))
                .resolve(artifactId)
                .resolve(version);
        if (!Files.exists(artifactDir)) {
            return;
        }
        try (var paths = Files.walk(artifactDir)) {
            paths.sorted(java.util.Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.delete(path);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
        }
    }

    static ItResult runMaven(Path projectDir, List<String> goals) throws MavenInvocationException {
        return runMaven(projectDir, goals, Map.of(), null);
    }

    static ItResult runMaven(Path projectDir, List<String> goals, Map<String, String> systemProperties, File userSettings)
            throws MavenInvocationException {
        InvocationRequest request = new DefaultInvocationRequest();
        request.setBaseDirectory(projectDir.toFile());
        request.setGoals(goals);
        request.setBatchMode(true);
        if (userSettings != null) {
            request.setUserSettingsFile(userSettings);
        }
        Properties properties = new Properties();
        systemProperties.forEach(properties::setProperty);
        request.setProperties(properties);
        return invoke(request);
    }

    private static ItResult invoke(InvocationRequest request) throws MavenInvocationException {
        StringBuilder output = new StringBuilder();
        request.setOutputHandler(line -> output.append(line).append('\n'));
        request.setErrorHandler(line -> output.append(line).append('\n'));

        Invoker invoker = new DefaultInvoker();
        invoker.setMavenExecutable(mavenExecutable);
        InvocationResult result = invoker.execute(request);
        return new ItResult(result.getExitCode(), output.toString());
    }

    static Map<String, String> mapOf(String... keyValues) {
        if (keyValues.length % 2 != 0) {
            throw new IllegalArgumentException("Expected an even number of key/value arguments");
        }
        Map<String, String> map = new LinkedHashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            map.put(keyValues[i], keyValues[i + 1]);
        }
        return map;
    }

    record ItResult(int exitCode, String output) {}
}
