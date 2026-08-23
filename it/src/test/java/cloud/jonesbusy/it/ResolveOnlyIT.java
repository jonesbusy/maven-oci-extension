package cloud.jonesbusy.it;

import cloud.jonesbusy.oci.common.OciAnnotations;
import cloud.jonesbusy.oci.common.OciArtifactTypes;
import cloud.jonesbusy.oci.common.OciCoordinates;
import cloud.jonesbusy.oci.common.OciMediaTypes;
import cloud.jonesbusy.oci.common.OciRepositoryPaths;

import land.oras.Annotations;
import land.oras.ArtifactType;
import land.oras.ContainerRef;
import land.oras.LocalPath;
import land.oras.Registry;
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
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pre-seeds the registry directly via oras-java (bypassing OciRepositoryConnector.put entirely) and
 * then resolves it back through a real Maven process, so the get()-side mapping/annotation logic is
 * validated independently of our own publish path.
 */
@Testcontainers(disabledWithoutDocker = true)
class ResolveOnlyIT extends ItSupport {

    @Container
    static final ZotUnsecureContainer REGISTRY = new ZotUnsecureContainer();

    @Test
    void resolvesAPreSeededArtifact(@TempDir Path scratch, @TempDir Path projectDir)
            throws IOException, MavenInvocationException {
        String groupId = "com.example";
        String artifactId = "preseeded";
        String version = "3.0.0";
        String jarContent = "pre-seeded-jar-content";

        // Uses the default local repository (not an empty/fresh one), since a fresh one would also
        // make oci-extension-core itself unresolvable there; defensively clear any leftovers from a
        // prior local run of this same test.
        deleteFromDefaultLocalRepository(groupId, artifactId, version);
        seedRegistry(scratch, groupId, artifactId, version, jarContent);

        copyTemplate("resolve-only", projectDir);
        String repositoryUrl = "it::default::oci+http://" + REGISTRY.getRegistry() + "/it";

        ItResult resolve = runMaven(
                projectDir,
                List.of("dependency:get"),
                mapOf("artifact", groupId + ":" + artifactId + ":" + version + ":jar", "remoteRepositories", repositoryUrl),
                null);
        assertTrue(resolve.exitCode() == 0, () -> "resolve failed:\n" + resolve.output());

        Path resolvedJar = Path.of(System.getProperty("user.home"), ".m2", "repository")
                .resolve(groupId.replace('.', '/') + "/" + artifactId + "/" + version + "/" + artifactId + "-" + version
                        + ".jar");
        assertTrue(Files.isRegularFile(resolvedJar), () -> "expected " + resolvedJar + " to have been resolved");
        assertEquals(jarContent, Files.readString(resolvedJar, StandardCharsets.UTF_8));
    }

    private void seedRegistry(Path scratch, String groupId, String artifactId, String version, String jarContent)
            throws IOException {
        Path pomFile = scratch.resolve("pom.xml");
        Path jarFile = scratch.resolve(artifactId + "-" + version + ".jar");
        Files.writeString(pomFile, "<project>seeded</project>", StandardCharsets.UTF_8);
        Files.writeString(jarFile, jarContent, StandardCharsets.UTF_8);

        Registry registry = Registry.builder().withInsecure(true).defaults().build();
        ContainerRef ref = ContainerRef.parse(REGISTRY.getRegistry() + "/it/" + OciRepositoryPaths.repository(groupId, artifactId)
                + ":" + OciRepositoryPaths.tag(version));

        OciCoordinates pomCoordinates = new OciCoordinates(groupId, artifactId, version, null, "pom");
        OciCoordinates jarCoordinates = new OciCoordinates(groupId, artifactId, version, null, "jar");
        Map<String, String> pomAnnotations = OciAnnotations.forArtifact(pomCoordinates, pomFile.getFileName().toString());
        Map<String, String> jarAnnotations = OciAnnotations.forArtifact(jarCoordinates, jarFile.getFileName().toString());

        Annotations annotations = Annotations.ofManifest(
                        Map.of(OciAnnotations.GROUP_ID, groupId, OciAnnotations.ARTIFACT_ID, artifactId, OciAnnotations.VERSION, version))
                .withFileAnnotations(pomFile.getFileName().toString(), pomAnnotations)
                .withFileAnnotations(jarFile.getFileName().toString(), jarAnnotations);

        registry.pushArtifact(
                ref,
                ArtifactType.from(OciArtifactTypes.PRIMARY),
                annotations,
                LocalPath.of(pomFile, OciMediaTypes.forExtension("pom")),
                LocalPath.of(jarFile, OciMediaTypes.forExtension("jar")));
    }
}
