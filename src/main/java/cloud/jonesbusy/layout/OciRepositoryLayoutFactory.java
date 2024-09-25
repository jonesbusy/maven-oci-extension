package cloud.jonesbusy.layout;

import org.eclipse.aether.internal.impl.Maven2RepositoryLayoutFactory;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.RepositorySystemSession;
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

    public static final String DEFAULT_CHECKSUMS_ALGORITHMS = "SHA-256";

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
        if (!"oci".equals(repository.getContentType())) {
            throw new NoRepositoryLayoutException(repository);
        }

        List<ChecksumAlgorithmFactory> checksumsAlgorithms = checksumAlgorithmFactorySelector.selectList(
                ConfigUtils.parseCommaSeparatedUniqueNames(ConfigUtils.getString(
                        session,
                        DEFAULT_CHECKSUMS_ALGORITHMS,
                        Maven2RepositoryLayoutFactory.CONFIG_PROP_CHECKSUMS_ALGORITHMS + "." + repository.getId(),
                        Maven2RepositoryLayoutFactory.CONFIG_PROP_CHECKSUMS_ALGORITHMS)));

            return new OciRepositoryLayout(checksumsAlgorithms, artifactPredicateFactory.newInstance(session));

    }

    @Override
    public float getPriority() {
        return 10.0f;
    }
}
