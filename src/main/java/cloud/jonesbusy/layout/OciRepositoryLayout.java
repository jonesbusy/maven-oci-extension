package cloud.jonesbusy.layout;


import org.codehaus.plexus.logging.Logger;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.metadata.Metadata;
import org.eclipse.aether.spi.connector.checksum.ChecksumAlgorithmFactory;
import org.eclipse.aether.spi.connector.layout.RepositoryLayout;

import javax.inject.Inject;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;

public class OciRepositoryLayout implements RepositoryLayout {

    @Inject
    private Logger log;

    @Override
    public List<ChecksumAlgorithmFactory> getChecksumAlgorithmFactories() {
        return List.of();
    }

    @Override
    public boolean hasChecksums(Artifact artifact) {
        return false;
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
        return null;
    }

    @Override
    public List<ChecksumLocation> getChecksumLocations(Artifact artifact, boolean upload, URI location) {
        return List.of();
    }

    @Override
    public List<ChecksumLocation> getChecksumLocations(Metadata metadata, boolean upload, URI location) {
        return List.of();
    }
}
