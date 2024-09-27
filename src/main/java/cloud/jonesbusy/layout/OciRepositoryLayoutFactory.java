package cloud.jonesbusy.layout;

import org.codehaus.plexus.logging.Logger;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.spi.artifact.ArtifactPredicateFactory;
import org.eclipse.aether.spi.connector.checksum.ChecksumAlgorithmFactory;
import org.eclipse.aether.spi.connector.checksum.ChecksumAlgorithmFactorySelector;
import org.eclipse.aether.spi.connector.layout.RepositoryLayout;
import org.eclipse.aether.spi.connector.layout.RepositoryLayoutFactory;
import org.eclipse.aether.transfer.NoRepositoryLayoutException;
import org.eclipse.aether.util.ConfigUtils;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import java.util.List;

import static java.util.Objects.requireNonNull;

/**
 * Factory for creating OCI repository layouts.
 */
@Singleton
@Named("oci")
public class OciRepositoryLayoutFactory implements RepositoryLayoutFactory {

    public static final String DEFAULT_CHECKSUMS_ALGORITHMS = "";
    private final ChecksumAlgorithmFactorySelector checksumAlgorithmFactorySelector;
    private final ArtifactPredicateFactory artifactPredicateFactory;
    @Inject
    private Logger log;

    @Inject
    public OciRepositoryLayoutFactory(
            ChecksumAlgorithmFactorySelector checksumAlgorithmFactorySelector,
            ArtifactPredicateFactory artifactPredicateFactory) {
        this.checksumAlgorithmFactorySelector = requireNonNull(checksumAlgorithmFactorySelector);
        this.artifactPredicateFactory = requireNonNull(artifactPredicateFactory);
    }

    @Override
    public RepositoryLayout newInstance(RepositorySystemSession session, RemoteRepository repository) throws NoRepositoryLayoutException {

        // Only OCI layout is supported by this factory
        log.info("Repository content type: " + repository.getContentType());
        if (!"oci".equals(repository.getProtocol())) {
            throw new NoRepositoryLayoutException(repository);
        }

        log.info("Creating OCI repository layout for repository " + repository.getId());
        List<ChecksumAlgorithmFactory> checksumsAlgorithms = checksumAlgorithmFactorySelector.selectList(
                ConfigUtils.parseCommaSeparatedUniqueNames(ConfigUtils.getString(
                        session,
                        DEFAULT_CHECKSUMS_ALGORITHMS)));

        return new OciRepositoryLayout(checksumsAlgorithms, artifactPredicateFactory.newInstance(session));

    }

    @Override
    public float getPriority() {
        return 10.0f;
    }
}
