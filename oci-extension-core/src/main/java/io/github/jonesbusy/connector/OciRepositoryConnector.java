package io.github.jonesbusy.connector;

import io.github.jonesbusy.oci.common.OciAnnotations;
import io.github.jonesbusy.oci.common.OciArtifactTypes;
import io.github.jonesbusy.oci.common.OciCoordinates;
import io.github.jonesbusy.oci.common.OciMediaTypes;
import io.github.jonesbusy.oci.common.OciRepositoryPaths;
import io.github.jonesbusy.oci.common.OciVersionCodec;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import land.oras.Annotations;
import land.oras.ArtifactType;
import land.oras.ContainerRef;
import land.oras.Layer;
import land.oras.LocalPath;
import land.oras.Manifest;
import land.oras.ManifestDescriptor;
import land.oras.Referrers;
import land.oras.Registry;
import land.oras.Tags;
import land.oras.exception.OrasException;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.metadata.Metadata;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.spi.connector.ArtifactDownload;
import org.eclipse.aether.spi.connector.ArtifactUpload;
import org.eclipse.aether.spi.connector.MetadataDownload;
import org.eclipse.aether.spi.connector.MetadataUpload;
import org.eclipse.aether.spi.connector.RepositoryConnector;
import org.eclipse.aether.transfer.ArtifactNotFoundException;
import org.eclipse.aether.transfer.ArtifactTransferException;
import org.eclipse.aether.transfer.MetadataNotFoundException;
import org.eclipse.aether.transfer.MetadataTransferException;
import org.eclipse.aether.transfer.NoRepositoryConnectorException;

/**
 * {@link RepositoryConnector} for the {@code oci}/{@code oci+http} protocols.
 * <p>
 * One OCI repository per {@code groupId:artifactId}, one tag per (sanitized) version. Each
 * version's manifest bundles the POM and main artifact as layers ("primary" -- no classifier);
 * classified artifacts (sources, javadoc, ...) are attached as separate referrer manifests
 * ("secondary"). See {@code io.github.jonesbusy.oci.common} for the mapping/annotation scheme shared
 * with any future consumer.
 * <p>
 * Not thread-safe, per the {@link RepositoryConnector} contract.
 */
final class OciRepositoryConnector implements RepositoryConnector {

    private final RemoteRepository repository;
    private final Registry registry;
    private final String baseRepositoryPrefix;
    private final Map<String, Manifest> primaryManifestCache = new ConcurrentHashMap<>();

    private volatile boolean closed;

    OciRepositoryConnector(RepositorySystemSession session, RemoteRepository repository)
            throws NoRepositoryConnectorException {
        this.repository = repository;
        boolean insecure = "oci+http".equals(repository.getProtocol());
        this.registry = Registry.builder().withInsecure(insecure).defaults().build();
        try {
            URI uri = new URI(repository.getUrl()).parseServerAuthority();
            String path = uri.getPath() == null ? "" : uri.getPath();
            this.baseRepositoryPrefix = uri.getRawAuthority() + path;
        } catch (URISyntaxException e) {
            throw new NoRepositoryConnectorException(repository, e.getMessage(), e);
        }
    }

    @Override
    public void get(
            Collection<? extends ArtifactDownload> artifactDownloads,
            Collection<? extends MetadataDownload> metadataDownloads) {
        checkOpen();
        if (artifactDownloads != null) {
            for (ArtifactDownload download : artifactDownloads) {
                getArtifact(download);
            }
        }
        if (metadataDownloads != null) {
            for (MetadataDownload download : metadataDownloads) {
                getMetadata(download);
            }
        }
    }

    @Override
    public void put(
            Collection<? extends ArtifactUpload> artifactUploads,
            Collection<? extends MetadataUpload> metadataUploads) {
        checkOpen();
        if (artifactUploads != null && !artifactUploads.isEmpty()) {
            putArtifacts(artifactUploads);
        }
        // maven-metadata.xml is synthesized from the registry's own tag list on resolve (see
        // getMetadata below), so there is nothing to store here; metadata uploads are no-ops.
    }

    @Override
    public void close() {
        closed = true;
    }

    private void checkOpen() {
        if (closed) {
            throw new IllegalStateException("Connector for repository '" + repository.getId() + "' is closed");
        }
    }

    // ---- resolve ----

    private void getArtifact(ArtifactDownload download) {
        Artifact artifact = download.getArtifact();
        OciCoordinates coordinates = toCoordinates(artifact);
        String gavKey = gavKey(coordinates.groupId(), coordinates.artifactId(), coordinates.version());
        ContainerRef primary = primaryRef(coordinates.groupId(), coordinates.artifactId(), coordinates.version());
        try {
            Layer layer = coordinates.hasClassifier()
                    ? findReferrerLayer(primary, gavKey, coordinates)
                    : findPrimaryLayer(primary, gavKey, coordinates);
            if (download.isExistenceCheck()) {
                return;
            }
            try (InputStream in = registry.fetchBlob(primary.withDigest(layer.getDigest()))) {
                writeAtomically(in, download.getPath());
            }
        } catch (LayerNotFoundException e) {
            download.setException(new ArtifactNotFoundException(artifact, repository, e.getMessage()));
        } catch (OrasException e) {
            if (isNotFound(e)) {
                download.setException(new ArtifactNotFoundException(artifact, repository, e.getMessage(), e));
            } else {
                download.setException(new ArtifactTransferException(artifact, repository, e.getMessage(), e));
            }
        } catch (Exception e) {
            download.setException(new ArtifactTransferException(artifact, repository, e.getMessage(), e));
        }
    }

    private Layer findPrimaryLayer(ContainerRef primary, String gavKey, OciCoordinates coordinates) {
        Manifest manifest = primaryManifest(primary, gavKey);
        return manifest.getLayers().stream()
                .filter(layer -> OciAnnotations.matchesExtension(layer.getAnnotations(), coordinates.extension())
                        && OciAnnotations.matchesClassifier(layer.getAnnotations(), coordinates.classifier()))
                .findFirst()
                .orElseThrow(() ->
                        new LayerNotFoundException("No layer for " + coordinates + " in primary manifest " + primary));
    }

    private Layer findReferrerLayer(ContainerRef primary, String gavKey, OciCoordinates coordinates) {
        Manifest primaryManifest = primaryManifest(primary, gavKey);
        String primaryDigest = primaryManifest.getDescriptor().getDigest();
        Referrers referrers = registry.getReferrers(primary.withDigest(primaryDigest), null);
        ManifestDescriptor match = referrers.getManifests().stream()
                .filter(md -> {
                    Map<String, String> annotations = md.getAnnotations();
                    Map<String, String> safe = annotations != null ? annotations : Map.of();
                    return OciAnnotations.matchesClassifier(safe, coordinates.classifier())
                            && OciAnnotations.matchesExtension(safe, coordinates.extension());
                })
                .findFirst()
                .orElseThrow(
                        () -> new LayerNotFoundException("No referrer for " + coordinates + " attached to " + primary));
        Manifest referrerManifest = registry.getManifest(primary.withDigest(match.getDigest()));
        List<Layer> layers = referrerManifest.getLayers();
        if (layers.isEmpty()) {
            throw new LayerNotFoundException("Referrer manifest for " + coordinates + " has no layers");
        }
        return layers.get(0);
    }

    private Manifest primaryManifest(ContainerRef primary, String gavKey) {
        return primaryManifestCache.computeIfAbsent(gavKey, key -> registry.getManifest(primary));
    }

    private void getMetadata(MetadataDownload download) {
        Metadata metadata = download.getMetadata();
        String version = metadata.getVersion();
        if (version != null && !version.isEmpty()) {
            download.setException(new MetadataNotFoundException(
                    metadata,
                    repository,
                    "SNAPSHOT-level maven-metadata.xml is not supported by this OCI connector yet"));
            return;
        }
        try {
            String repositoryPath = OciRepositoryPaths.repository(metadata.getGroupId(), metadata.getArtifactId());
            ContainerRef ref = ContainerRef.parse(baseRepositoryPrefix + "/" + repositoryPath);
            Tags tags = registry.getTags(ref);
            List<String> versions = new ArrayList<>();
            for (String tag : tags.tags()) {
                try {
                    versions.add(OciVersionCodec.decode(tag));
                } catch (IllegalArgumentException e) {
                    // Not a tag we produced (e.g. hand-pushed); skip rather than fail the whole listing.
                }
            }
            if (versions.isEmpty()) {
                download.setException(new MetadataNotFoundException(metadata, repository));
                return;
            }
            String xml =
                    MavenMetadataXml.build(metadata.getGroupId(), metadata.getArtifactId(), versions, Instant.now());
            writeAtomically(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)), download.getPath());
        } catch (OrasException e) {
            if (isNotFound(e)) {
                download.setException(new MetadataNotFoundException(metadata, repository));
            } else {
                download.setException(new MetadataTransferException(metadata, repository, e.getMessage(), e));
            }
        } catch (Exception e) {
            download.setException(new MetadataTransferException(metadata, repository, e.getMessage(), e));
        }
    }

    // ---- publish ----

    private void putArtifacts(Collection<? extends ArtifactUpload> uploads) {
        Map<String, List<ArtifactUpload>> byGav = new LinkedHashMap<>();
        for (ArtifactUpload upload : uploads) {
            Artifact artifact = upload.getArtifact();
            String key = gavKey(artifact.getGroupId(), artifact.getArtifactId(), artifact.getVersion());
            byGav.computeIfAbsent(key, k -> new ArrayList<>()).add(upload);
        }
        for (Map.Entry<String, List<ArtifactUpload>> entry : byGav.entrySet()) {
            pushGroup(entry.getKey(), entry.getValue());
        }
    }

    private void pushGroup(String gavKey, List<ArtifactUpload> group) {
        Artifact first = group.get(0).getArtifact();
        ContainerRef primary = primaryRef(first.getGroupId(), first.getArtifactId(), first.getVersion());

        List<ArtifactUpload> primaryUploads = new ArrayList<>();
        List<ArtifactUpload> secondaryUploads = new ArrayList<>();
        for (ArtifactUpload upload : group) {
            if (isPrimary(upload.getArtifact())) {
                primaryUploads.add(upload);
            } else {
                secondaryUploads.add(upload);
            }
        }

        if (primaryUploads.isEmpty()) {
            ArtifactTransferException ex = new ArtifactTransferException(
                    first,
                    repository,
                    "No primary artifact (pom/main artifact without classifier) to push for " + gavKey);
            group.forEach(upload -> upload.setException(ex));
            return;
        }

        if (!pushPrimary(gavKey, primary, primaryUploads)) {
            // Exceptions already set on primaryUploads; a referrer needs a primary manifest to
            // attach to, so fail secondaries too rather than leaving them silently unpushed.
            ArtifactTransferException ex = new ArtifactTransferException(
                    first,
                    repository,
                    "Primary artifact push failed for " + gavKey + "; skipping classified artifacts");
            secondaryUploads.forEach(upload -> upload.setException(ex));
            return;
        }

        for (ArtifactUpload upload : secondaryUploads) {
            pushSecondary(primary, upload);
        }
    }

    private boolean pushPrimary(String gavKey, ContainerRef primary, List<ArtifactUpload> uploads) {
        try {
            Artifact first = uploads.get(0).getArtifact();
            Annotations annotations = Annotations.ofManifest(Map.of(
                    OciAnnotations.GROUP_ID, first.getGroupId(),
                    OciAnnotations.ARTIFACT_ID, first.getArtifactId(),
                    OciAnnotations.VERSION, first.getVersion()));
            LocalPath[] paths = new LocalPath[uploads.size()];
            for (int i = 0; i < uploads.size(); i++) {
                Artifact artifact = uploads.get(i).getArtifact();
                // oras-java keys per-file annotations by the uploaded file's own basename (see
                // OCI#pushLayer), not by any name of our choosing -- so the key here must be that
                // same basename or the annotations silently never attach to the resulting layer.
                String fileName = uploads.get(i).getPath().getFileName().toString();
                annotations = annotations.withFileAnnotations(
                        fileName, OciAnnotations.forArtifact(toCoordinates(artifact), fileName));
                paths[i] = LocalPath.of(uploads.get(i).getPath(), OciMediaTypes.forExtension(artifact.getExtension()));
            }
            Manifest pushed =
                    registry.pushArtifact(primary, ArtifactType.from(OciArtifactTypes.PRIMARY), annotations, paths);
            primaryManifestCache.put(gavKey, pushed);
            return true;
        } catch (Exception e) {
            ArtifactTransferException ex =
                    new ArtifactTransferException(uploads.get(0).getArtifact(), repository, e.getMessage(), e);
            uploads.forEach(upload -> upload.setException(ex));
            return false;
        }
    }

    private void pushSecondary(ContainerRef primary, ArtifactUpload upload) {
        Artifact artifact = upload.getArtifact();
        try {
            OciCoordinates coordinates = toCoordinates(artifact);
            String fileName = upload.getPath().getFileName().toString();
            Map<String, String> fileAnnotations = OciAnnotations.forArtifact(coordinates, fileName);
            Annotations annotations =
                    Annotations.ofManifest(fileAnnotations).withFileAnnotations(fileName, fileAnnotations);
            registry.attachArtifact(
                    primary,
                    ArtifactType.from(OciArtifactTypes.SECONDARY),
                    annotations,
                    LocalPath.of(upload.getPath(), OciMediaTypes.forExtension(artifact.getExtension())));
        } catch (Exception e) {
            upload.setException(new ArtifactTransferException(artifact, repository, e.getMessage(), e));
        }
    }

    // ---- shared helpers ----

    private ContainerRef primaryRef(String groupId, String artifactId, String version) {
        return ContainerRef.parse(baseRepositoryPrefix + "/" + OciRepositoryPaths.repository(groupId, artifactId) + ":"
                + OciRepositoryPaths.tag(version));
    }

    private static OciCoordinates toCoordinates(Artifact artifact) {
        return new OciCoordinates(
                artifact.getGroupId(),
                artifact.getArtifactId(),
                artifact.getVersion(),
                artifact.getClassifier(),
                artifact.getExtension());
    }

    private static boolean isPrimary(Artifact artifact) {
        String classifier = artifact.getClassifier();
        return classifier == null || classifier.isEmpty();
    }

    private static String gavKey(String groupId, String artifactId, String version) {
        return groupId + ":" + artifactId + ":" + version;
    }

    private static boolean isNotFound(OrasException e) {
        return e.getStatusCode() == 404;
    }

    /**
     * Writes {@code in} to {@code target}, first copying into a sibling temp file and then moving
     * it into place, so a failed/interrupted transfer never leaves a partially-written target.
     */
    private static void writeAtomically(InputStream in, Path target) throws IOException {
        Path parent = target.toAbsolutePath().getParent();
        Files.createDirectories(parent);
        Path tempFile = Files.createTempFile(parent, target.getFileName().toString(), ".tmp");
        try {
            Files.copy(in, tempFile, StandardCopyOption.REPLACE_EXISTING);
            try {
                Files.move(tempFile, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tempFile, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }

    private static final class LayerNotFoundException extends RuntimeException {
        LayerNotFoundException(String message) {
            super(message);
        }
    }
}
