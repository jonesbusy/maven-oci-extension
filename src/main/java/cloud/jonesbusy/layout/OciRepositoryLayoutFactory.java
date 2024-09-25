package cloud.jonesbusy.layout;

import org.apache.maven.artifact.repository.layout.DefaultRepositoryLayout;
import org.codehaus.plexus.logging.Logger;
import org.eclipse.aether.internal.impl.Maven2RepositoryLayoutFactory;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.spi.connector.layout.RepositoryLayout;
import org.eclipse.aether.spi.connector.layout.RepositoryLayoutFactory;
import org.eclipse.aether.transfer.NoRepositoryLayoutException;

import javax.inject.Inject;
import javax.inject.Named;

@Named("oci")
public class OciRepositoryLayoutFactory implements RepositoryLayoutFactory {

    @Inject
    private Logger log;

    @Inject
    private Maven2RepositoryLayoutFactory mavenLayoutFactory;

    @Override
    public RepositoryLayout newInstance(RepositorySystemSession session, RemoteRepository repository)
            throws NoRepositoryLayoutException {
        if (repository.getProtocol().startsWith("oci://")) {
            log.info("Creating OCI repository layout for " + repository.getUrl());
            RemoteRepository defaultRepository = new RemoteRepository.Builder(repository)
                    .setContentType("default")
                    .build();
            return mavenLayoutFactory.newInstance(session, defaultRepository);
        }

        throw new NoRepositoryLayoutException(repository);
    }

    @Override
    public float getPriority() {
        return 10.0f;  // Higher priority to override default layouts
    }
}
