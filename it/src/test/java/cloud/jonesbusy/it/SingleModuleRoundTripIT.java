package cloud.jonesbusy.it;

import land.oras.utils.ZotUnsecureContainer;
import org.apache.maven.shared.invoker.MavenInvocationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Deploys a single-module project directly to an oci+http:// repository (no njord involved) and
 * then resolves it back, proving the connector's put()/get() round-trip through a real Maven 4
 * process rather than a direct oras-java call.
 * <p>
 * Resolution uses the default local repository (not an empty/fresh one) because a fresh repository
 * would also make oci-extension-core itself unresolvable there -- it only exists as a SNAPSHOT in
 * the default repository this harness installed it into. To still prove a genuine fetch rather than
 * a cache hit, the target artifact is deleted from the default repository between the deploy and
 * resolve steps.
 */
@Testcontainers(disabledWithoutDocker = true)
class SingleModuleRoundTripIT extends ItSupport {

    @Container
    static final ZotUnsecureContainer REGISTRY = new ZotUnsecureContainer();

    @Test
    void deploysAndResolvesBackTheSameJar(@TempDir Path projectDir, @TempDir Path consumerDir)
            throws IOException, MavenInvocationException {
        copyTemplate("single-module", projectDir);
        // dependency:get must run from a project whose own coordinates differ from the target
        // artifact: Maven recognizes "resolving the reactor's own artifact" and shortcuts to the
        // local build output (target/classes) instead of actually hitting the remote repository,
        // which would make this test pass without ever exercising OciRepositoryConnector.get().
        copyTemplate("resolve-only", consumerDir);
        String repositoryUrl = "it::default::oci+http://" + REGISTRY.getRegistry() + "/it";

        ItResult deploy = runMaven(projectDir, List.of("deploy"), mapOf("altDeploymentRepository", repositoryUrl), null);
        assertTrue(deploy.exitCode() == 0, () -> "deploy failed:\n" + deploy.output());

        deleteFromDefaultLocalRepository("com.example", "single", "1.0.0");

        ItResult resolve = runMaven(
                consumerDir,
                List.of("dependency:get"),
                mapOf("artifact", "com.example:single:1.0.0:jar", "remoteRepositories", repositoryUrl),
                null);
        assertTrue(resolve.exitCode() == 0, () -> "resolve failed:\n" + resolve.output());

        Path resolvedJar = defaultLocalRepositoryPath("com/example/single/1.0.0/single-1.0.0.jar");
        assertTrue(Files.isRegularFile(resolvedJar), () -> "expected " + resolvedJar + " to have been resolved");

        Path resolvedPom = defaultLocalRepositoryPath("com/example/single/1.0.0/single-1.0.0.pom");
        ItResult resolvePom = runMaven(
                consumerDir,
                List.of("dependency:get"),
                mapOf("artifact", "com.example:single:1.0.0:pom", "remoteRepositories", repositoryUrl),
                null);
        assertTrue(resolvePom.exitCode() == 0, () -> "pom resolve failed:\n" + resolvePom.output());
        assertTrue(Files.isRegularFile(resolvedPom), () -> "expected " + resolvedPom + " to have been resolved");
        // Maven 4 deploys the generated "consumer POM", not the source pom.xml byte-for-byte, so
        // assert on its logical content rather than exact equality with the source file.
        String pomContent = Files.readString(resolvedPom, StandardCharsets.UTF_8);
        assertTrue(pomContent.contains("<artifactId>single</artifactId>"), pomContent);
        assertTrue(pomContent.contains("<version>1.0.0</version>"), pomContent);
    }

    private static Path defaultLocalRepositoryPath(String relative) {
        return Path.of(System.getProperty("user.home"), ".m2", "repository").resolve(relative);
    }
}
