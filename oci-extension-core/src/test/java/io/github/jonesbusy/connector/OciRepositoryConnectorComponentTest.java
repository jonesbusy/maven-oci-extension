package io.github.jonesbusy.connector;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import land.oras.utils.ZotUnsecureContainer;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.metadata.DefaultMetadata;
import org.eclipse.aether.metadata.Metadata;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.spi.connector.ArtifactDownload;
import org.eclipse.aether.spi.connector.ArtifactUpload;
import org.eclipse.aether.spi.connector.MetadataDownload;
import org.eclipse.aether.spi.connector.RepositoryConnector;
import org.eclipse.aether.transfer.ArtifactNotFoundException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Exercises {@link OciRepositoryConnector} against a real registry (Testcontainers Zot, plain
 * HTTP/insecure), the same way Maven's resolver would drive it via a batched get()/put().
 */
@Testcontainers(disabledWithoutDocker = true)
class OciRepositoryConnectorComponentTest {

    @Container
    static final ZotUnsecureContainer REGISTRY = new ZotUnsecureContainer();

    @TempDir
    Path tempDir;

    private RepositoryConnector connector;

    @BeforeEach
    void createConnector() throws Exception {
        RemoteRepository repository =
                new RemoteRepository.Builder("it", "default", "oci+http://" + REGISTRY.getRegistry() + "/it").build();
        connector = new OciRepositoryConnectorFactory().newInstance(null, repository);
    }

    @AfterEach
    void closeConnector() {
        connector.close();
    }

    @Test
    void roundTripsPomAndMainArtifactWithoutClassifier() throws IOException {
        String groupId = "com.example";
        String artifactId = "demo";
        String version = "1.0.0";

        Path pomFile = writeFile("pom.xml", "<project>pom-content</project>");
        Path jarFile = writeFile("demo.jar", "jar-content");

        Artifact pom = new DefaultArtifact(groupId, artifactId, "pom", version);
        Artifact jar = new DefaultArtifact(groupId, artifactId, "jar", version);

        ArtifactUpload pomUpload = new ArtifactUpload(pom, pomFile);
        ArtifactUpload jarUpload = new ArtifactUpload(jar, jarFile);
        connector.put(List.of(pomUpload, jarUpload), null);

        assertNoException(pomUpload);
        assertNoException(jarUpload);

        ArtifactDownload pomDownload = download(pom, tempDir.resolve("out.pom"));
        ArtifactDownload jarDownload = download(jar, tempDir.resolve("out.jar"));
        connector.get(List.of(pomDownload, jarDownload), null);

        assertNoException(pomDownload);
        assertNoException(jarDownload);
        assertEquals("<project>pom-content</project>", Files.readString(pomDownload.getPath()));
        assertEquals("jar-content", Files.readString(jarDownload.getPath()));
    }

    @Test
    void roundTripsClassifiedArtifactViaReferrers() throws IOException {
        String groupId = "com.example";
        String artifactId = "demo-sources";
        String version = "2.0.0";

        Path pomFile = writeFile("pom.xml", "pom-body");
        Path jarFile = writeFile("main.jar", "main-body");
        Path sourcesFile = writeFile("sources.jar", "sources-body");

        Artifact pom = new DefaultArtifact(groupId, artifactId, "pom", version);
        Artifact jar = new DefaultArtifact(groupId, artifactId, "jar", version);
        Artifact sources = new DefaultArtifact(groupId, artifactId, "sources", "jar", version);

        ArtifactUpload pomUpload = new ArtifactUpload(pom, pomFile);
        ArtifactUpload jarUpload = new ArtifactUpload(jar, jarFile);
        ArtifactUpload sourcesUpload = new ArtifactUpload(sources, sourcesFile);
        connector.put(List.of(pomUpload, jarUpload, sourcesUpload), null);

        assertNoException(pomUpload);
        assertNoException(jarUpload);
        assertNoException(sourcesUpload);

        ArtifactDownload sourcesDownload = download(sources, tempDir.resolve("out-sources.jar"));
        connector.get(List.of(sourcesDownload), null);

        assertNoException(sourcesDownload);
        assertEquals("sources-body", Files.readString(sourcesDownload.getPath()));
    }

    @Test
    void missingArtifactReportsNotFound() {
        Artifact missing = new DefaultArtifact("com.example", "does-not-exist", "jar", "9.9.9");
        ArtifactDownload download = download(missing, tempDir.resolve("missing.jar"));

        connector.get(List.of(download), null);

        assertTrue(download.getException() instanceof ArtifactNotFoundException, "expected ArtifactNotFoundException");
    }

    @Test
    void existenceCheckDoesNotWriteFile() throws IOException {
        String groupId = "com.example";
        String artifactId = "demo-exists";
        String version = "1.0.0";
        Artifact pom = new DefaultArtifact(groupId, artifactId, "pom", version);

        ArtifactUpload upload = new ArtifactUpload(pom, writeFile("pom.xml", "pom-body"));
        connector.put(List.of(upload), null);
        assertNoException(upload);

        Path target = tempDir.resolve("should-not-exist.pom");
        ArtifactDownload download = download(pom, target);
        download.setExistenceCheck(true);
        connector.get(List.of(download), null);

        assertNoException(download);
        assertTrue(Files.notExists(target), "existence check must not write the file");
    }

    @Test
    void synthesizesGaLevelMetadataFromTags() throws IOException {
        String groupId = "com.example";
        String artifactId = "demo-metadata";

        for (String version : List.of("1.0.0", "1.1.0")) {
            Artifact pom = new DefaultArtifact(groupId, artifactId, "pom", version);
            ArtifactUpload upload = new ArtifactUpload(pom, writeFile("pom-" + version + ".xml", "pom-body"));
            connector.put(List.of(upload), null);
            assertNoException(upload);
        }

        Metadata metadata = new DefaultMetadata(groupId, artifactId, "maven-metadata.xml", Metadata.Nature.RELEASE);
        MetadataDownload download =
                new MetadataDownload(metadata, "test", tempDir.resolve("maven-metadata.xml"), "warn");
        connector.get(null, List.of(download));

        assertNoException(download);
        String xml = Files.readString(download.getPath());
        assertTrue(xml.contains("<version>1.0.0</version>"), xml);
        assertTrue(xml.contains("<version>1.1.0</version>"), xml);
        assertTrue(xml.contains("<latest>1.1.0</latest>"), xml);
    }

    private static ArtifactDownload download(Artifact artifact, Path target) {
        return new ArtifactDownload()
                .setArtifact(artifact)
                .setRequestContext("test")
                .setPath(target)
                .setChecksumPolicy("warn");
    }

    private Path writeFile(String name, String content) throws IOException {
        Path file = tempDir.resolve(name);
        Files.writeString(file, content, StandardCharsets.UTF_8);
        return file;
    }

    private static void assertNoException(ArtifactUpload upload) {
        assertNull(upload.getException(), () -> String.valueOf(upload.getException()));
    }

    private static void assertNoException(ArtifactDownload download) {
        assertNull(download.getException(), () -> String.valueOf(download.getException()));
    }

    private static void assertNoException(MetadataDownload download) {
        assertNull(download.getException(), () -> String.valueOf(download.getException()));
    }
}
