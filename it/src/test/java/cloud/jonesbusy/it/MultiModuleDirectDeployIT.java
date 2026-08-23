package cloud.jonesbusy.it;

import cloud.jonesbusy.oci.common.OciRepositoryPaths;

import land.oras.ContainerRef;
import land.oras.Registry;
import land.oras.utils.ZotUnsecureContainer;

import org.apache.maven.shared.invoker.MavenInvocationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A 2-module reactor deployed with a single {@code mvn deploy -DaltDeploymentRepository=...}, no
 * staging tool involved: each module publishes straight to its own OCI repository via
 * OciRepositoryConnector, exactly like it would to any other remote repository. This is the whole
 * publish story for this extension -- point at an oci+http:// (or oci://) repository and deploy.
 */
@Testcontainers(disabledWithoutDocker = true)
class MultiModuleDirectDeployIT extends ItSupport {

    @Container
    static final ZotUnsecureContainer REGISTRY = new ZotUnsecureContainer();

    @Test
    void bothModulesPublishIndependently(@TempDir Path projectDir) throws IOException, MavenInvocationException {
        copyTemplate("multi-module-direct", projectDir);
        String repositoryUrl = "it::default::oci+http://" + REGISTRY.getRegistry() + "/it";

        ItResult deploy = runMaven(projectDir, List.of("deploy"), mapOf("altDeploymentRepository", repositoryUrl), null);
        assertTrue(deploy.exitCode() == 0, () -> "deploy failed:\n" + deploy.output());

        Registry registry = Registry.builder().withInsecure(true).defaults().build();
        assertDoesNotThrow(
                () -> registry.getManifest(moduleRef(registry, "module-a")), "module-a manifest should be published");
        assertDoesNotThrow(
                () -> registry.getManifest(moduleRef(registry, "module-b")), "module-b manifest should be published");
    }

    private ContainerRef moduleRef(Registry registry, String artifactId) {
        return ContainerRef.parse(REGISTRY.getRegistry() + "/it/" + OciRepositoryPaths.repository("com.example", artifactId)
                + ":" + OciRepositoryPaths.tag("1.0.0"));
    }
}
