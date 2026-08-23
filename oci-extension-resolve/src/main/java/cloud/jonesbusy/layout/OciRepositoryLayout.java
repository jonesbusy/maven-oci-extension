package cloud.jonesbusy.layout;


import org.codehaus.plexus.logging.Logger;
import org.codehaus.plexus.logging.console.ConsoleLogger;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.metadata.Metadata;
import org.eclipse.aether.spi.artifact.ArtifactPredicate;
import org.eclipse.aether.spi.connector.checksum.ChecksumAlgorithmFactory;
import org.eclipse.aether.spi.connector.layout.RepositoryLayout;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static java.util.Objects.requireNonNull;

/**
 * Layout for OCI repositories.
 */
public class OciRepositoryLayout implements RepositoryLayout {

    private static final Logger LOG = new ConsoleLogger(Logger.LEVEL_DEBUG, OciRepositoryLayout.class.getName());

    /**
     * The checksum algorithms configured for this layout.
     */
    private final List<ChecksumAlgorithmFactory> configuredChecksumAlgorithms;
    /**
     * The artifact predicate
     */
    private final ArtifactPredicate artifactPredicate;

    /**
     * Creates a new OCI repository layout.
     *
     * @param configuredChecksumAlgorithms The checksum algorithms configured for this layout.
     * @param artifactPredicate            The artifact predicate.
     */
    public OciRepositoryLayout(List<ChecksumAlgorithmFactory> configuredChecksumAlgorithms,
                               ArtifactPredicate artifactPredicate) {
        this.configuredChecksumAlgorithms = Collections.unmodifiableList(configuredChecksumAlgorithms);
        this.artifactPredicate = requireNonNull(artifactPredicate);
    }

    @Override
    public List<ChecksumAlgorithmFactory> getChecksumAlgorithmFactories() {
        return configuredChecksumAlgorithms;
    }

    @Override
    public boolean hasChecksums(Artifact artifact) {
        return !artifactPredicate.isWithoutChecksum(artifact);
    }

    @Override
    public URI getLocation(Artifact artifact, boolean upload) {
        try {
            return new URI(artifact.getGroupId() + "/" + artifact.getArtifactId());
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public URI getLocation(Metadata metadata, boolean upload) {
        StringBuilder path = new StringBuilder(128);
        if (!metadata.getGroupId().isEmpty()) {
            path.append(metadata.getGroupId().replace('.', '/')).append('/');
            if (!metadata.getArtifactId().isEmpty()) {
                path.append(metadata.getArtifactId()).append('/');
            }
        }
        path.append(metadata.getType());
        path.append(":latest");
        LOG.info("Metadata path: " + path);
        return toUri(path.toString().toLowerCase());
    }

    @Override
    public List<ChecksumLocation> getChecksumLocations(Artifact artifact, boolean upload, URI location) {
        if (artifactPredicate.isWithoutChecksum(artifact) || artifactPredicate.isChecksum(artifact)) {
            return Collections.emptyList();
        }
        return getChecksumLocations(location);
    }

    @Override
    public List<ChecksumLocation> getChecksumLocations(Metadata metadata, boolean upload, URI location) {
        return getChecksumLocations(location);
    }

    private List<ChecksumLocation> getChecksumLocations(URI location) {
        List<ChecksumLocation> checksumLocations = new ArrayList<>(configuredChecksumAlgorithms.size());
        for (ChecksumAlgorithmFactory checksumAlgorithmFactory : configuredChecksumAlgorithms) {
            checksumLocations.add(ChecksumLocation.forLocation(location, checksumAlgorithmFactory));
        }
        return checksumLocations;
    }

    private URI toUri(String path) {
        try {
            return new URI(null, null, path, null);
        } catch (URISyntaxException e) {
            throw new IllegalStateException(e);
        }
    }

}
