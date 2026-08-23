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
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Multi-module reactor staged via njord (distributionManagement points at "njord:") and then
 * published via njord's stock "deploy" publisher pointed at our oci+http:// connector, proving
 * njord's real local staging + our connector's batched put() work together, with no njord-specific
 * code in this repo (see the Phase 1 spike notes for why).
 * <p>
 * This is deliberately two Maven invocations, not one with {@code njord.autoPublish=true}: the
 * "deploy" publisher's target repository is resolved from the very same
 * {@code altReleaseDeploymentRepository} property that maven-deploy-plugin itself reads to pick
 * *its own* deploy target, so setting it up front would make module-a/module-b's own deploy phase
 * bypass njord's "njord:" staging and push straight to the real registry -- defeating the point.
 * Splitting into "stage" then "publish" (matching njord's own documented two-step workflow) lets
 * each step supply that property with a different, correct meaning.
 */
@Testcontainers(disabledWithoutDocker = true)
class MultiModuleReactorNjordIT extends ItSupport {

    @Container
    static final ZotUnsecureContainer REGISTRY = new ZotUnsecureContainer();

    @Test
    void publishesBothModulesAfterTheWholeReactorSucceeds(@TempDir Path projectDir)
            throws IOException, MavenInvocationException {
        copyTemplate("multi-module", projectDir);

        ItResult stage = runMaven(projectDir, List.of("deploy"), Map.of(), null);
        assertTrue(stage.exitCode() == 0, () -> "staging deploy failed:\n" + stage.output());
        assertTrue(
                stage.output().contains("file:///") && stage.output().contains(".njord/"),
                () -> "expected staging to go through njord's local store, not straight to the registry:\n"
                        + stage.output());

        ItResult publish = runMaven(
                projectDir,
                List.of("eu.maveniverse.maven.plugins:njord:0.9.10:publish"),
                mapOf(
                        "njord.publisher", "deploy",
                        "altReleaseDeploymentRepository", "my-oci::default::oci+http://" + REGISTRY.getRegistry() + "/it"),
                null);
        assertTrue(publish.exitCode() == 0, () -> "njord:publish failed:\n" + publish.output());

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
