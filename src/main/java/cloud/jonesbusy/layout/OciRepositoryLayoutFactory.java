package cloud.jonesbusy.layout;

import org.eclipse.aether.internal.impl.Maven2RepositoryLayoutFactory;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.spi.connector.layout.RepositoryLayout;
import org.eclipse.aether.spi.connector.layout.RepositoryLayoutFactory;
import org.eclipse.aether.transfer.NoRepositoryLayoutException;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

/**
 * Factory for creating OCI repository layouts.
 */
@Singleton
@Named("oci")
public class OciRepositoryLayoutFactory implements RepositoryLayoutFactory {

    @Override
    public RepositoryLayout newInstance(RepositorySystemSession session, RemoteRepository repository) throws NoRepositoryLayoutException {
        if ("oci".equals(repository.getContentType())) {
            return new OciRepositoryLayout();
        }
        throw new NoRepositoryLayoutException(repository, "Unsupported repository content type: " + repository.getContentType());
    }

    @Override
    public float getPriority() {
        return 10.0f;
    }
}
