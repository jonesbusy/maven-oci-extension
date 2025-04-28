package cloud.jonesbusy.layout;

import org.codehaus.plexus.logging.Logger;
import org.codehaus.plexus.logging.console.ConsoleLogger;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.spi.artifact.ArtifactPredicateFactory;
import org.eclipse.aether.spi.connector.checksum.ChecksumAlgorithmFactory;
import org.eclipse.aether.spi.connector.checksum.ChecksumAlgorithmFactorySelector;
import org.eclipse.aether.spi.connector.layout.RepositoryLayout;
import org.eclipse.aether.spi.connector.layout.RepositoryLayoutFactory;
import org.eclipse.aether.transfer.NoRepositoryLayoutException;
import org.eclipse.aether.util.ConfigUtils;

import org.apache.maven.api.di.Singleton;
import org.apache.maven.api.di.Named;
import org.apache.maven.api.di.Inject;
import java.util.List;

import static java.util.Objects.requireNonNull;

/**
 * Factory for creating OCI repository layouts.
 */
@Singleton
@Named("oci")
public class OciRepositoryLayoutFactory implements RepositoryLayoutFactory {

    /**
     * The logger.
     */
    private static final Logger LOG = new ConsoleLogger(Logger.LEVEL_DEBUG, OciRepositoryLayoutFactory.class.getName());

    public static final String DEFAULT_CHECKSUMS_ALGORITHMS = "";
    private final ChecksumAlgorithmFactorySelector checksumAlgorithmFactorySelector;
    private final ArtifactPredicateFactory artifactPredicateFactory;

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
        LOG.info("Repository content type: " + repository.getContentType());
        if (!"oci".equals(repository.getProtocol()) && !"oci+http".equals(repository.getProtocol())) {
            throw new NoRepositoryLayoutException(repository);
        }

        LOG.info("Creating OCI repository layout for repository " + repository.getId());
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
