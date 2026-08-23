package cloud.jonesbusy.it;

import cloud.jonesbusy.oci.common.OciRepositoryPaths;

import land.oras.ContainerRef;
import land.oras.Registry;
import land.oras.exception.OrasException;
import land.oras.utils.ZotUnsecureContainer;

import org.apache.maven.shared.invoker.MavenInvocationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Same shape as {@link MultiModuleReactorNjordIT}, but module-b has a test that always fails. Proves
 * njord's autoPublish only runs when the whole reactor session succeeds -- a failed build leaves
 * nothing pushed to the registry, not even module-a, which would have finished its own build steps
 * before module-b's failure aborted the session.
 */
@Testcontainers(disabledWithoutDocker = true)
class ReactorFailureIT extends ItSupport {

    @Container
    static final ZotUnsecureContainer REGISTRY = new ZotUnsecureContainer();

    @Test
    void nothingIsPublishedWhenTheReactorFails(@TempDir Path projectDir) throws IOException, MavenInvocationException {
        copyTemplate("reactor-failure", projectDir);

        // No altReleaseDeploymentRepository here: that property is read by BOTH maven-deploy-plugin
        // itself (to pick its own deploy target) and njord's publisher config, so setting it would
        // make module-a's deploy phase bypass njord's "njord:" staging and push straight to the real
        // registry -- defeating the point of this test. Staging goes through njord via the project's
        // own distributionManagement (see the template's pom.xml); autoPublish's own target
        // resolution is irrelevant here since the reactor fails before publish would matter.
        ItResult deploy = runMaven(projectDir, List.of("deploy"), mapOf("njord.autoPublish", "true"), null);
        assertFalse(deploy.exitCode() == 0, () -> "expected the build to fail, but it succeeded:\n" + deploy.output());

        Registry registry = Registry.builder().withInsecure(true).defaults().build();
        assertThrows(
                OrasException.class,
                () -> registry.getManifest(moduleRef(registry, "module-a-rf")),
                "module-a-rf must not have been published since the reactor failed");
        assertThrows(
                OrasException.class,
                () -> registry.getManifest(moduleRef(registry, "module-b-rf")),
                "module-b-rf must not have been published since the reactor failed");
    }

    private ContainerRef moduleRef(Registry registry, String artifactId) {
        return ContainerRef.parse(REGISTRY.getRegistry() + "/it/" + OciRepositoryPaths.repository("com.example", artifactId)
                + ":" + OciRepositoryPaths.tag("1.0.0"));
    }
}
