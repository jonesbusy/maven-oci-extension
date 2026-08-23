package io.github.jonesbusy.it;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

/**
 * Locates a Maven 4.x executable to drive the integration tests with. This extension only compiles
 * against Maven 4's resolver API (org.apache.maven.resolver 2.x): loading it into a Maven 3.9.x
 * process (bundled resolver 1.9.x) would fail at runtime, so the IT harness must not silently fall
 * back to whatever "mvn" happens to be on PATH without checking its version.
 */
final class Maven4Locator {

    private Maven4Locator() {}

    static Optional<File> findMavenExecutable() {
        String override = System.getenv("OCI_IT_MAVEN_HOME");
        if (override != null && !override.isBlank()) {
            return executableUnder(new File(override));
        }

        // A common local-dev convenience: sdkman installs each Maven version under its own directory.
        File sdkmanCandidates = new File(System.getProperty("user.home"), ".sdkman/candidates/maven");
        File[] versions = sdkmanCandidates.isDirectory() ? sdkmanCandidates.listFiles() : null;
        if (versions != null) {
            for (File version : versions) {
                if (version.getName().startsWith("4.")) {
                    Optional<File> found = executableUnder(version);
                    if (found.isPresent()) {
                        return found;
                    }
                }
            }
        }

        return pathMavenIfVersion4();
    }

    private static Optional<File> executableUnder(File mavenHome) {
        File exe = new File(mavenHome, "bin/mvn");
        return exe.isFile() ? Optional.of(exe) : Optional.empty();
    }

    private static Optional<File> pathMavenIfVersion4() {
        try {
            Process process =
                    new ProcessBuilder("mvn", "-v").redirectErrorStream(true).start();
            String output;
            try (var in = process.getInputStream()) {
                output = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
            boolean finished = process.waitFor(30, java.util.concurrent.TimeUnit.SECONDS);
            if (finished && process.exitValue() == 0 && output.contains("Apache Maven 4.")) {
                return Optional.of(new File("mvn"));
            }
        } catch (IOException e) {
            // no mvn on PATH; fall through to empty
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return Optional.empty();
    }
}
