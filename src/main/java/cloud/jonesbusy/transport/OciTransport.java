package cloud.jonesbusy.transport;

import land.oras.Registry;
import land.oras.auth.UsernamePasswordProvider;
import org.eclipse.aether.ConfigurationProperties;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.repository.AuthenticationContext;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.spi.connector.transport.AbstractTransporter;
import org.eclipse.aether.spi.connector.transport.GetTask;
import org.eclipse.aether.spi.connector.transport.PeekTask;
import org.eclipse.aether.spi.connector.transport.PutTask;
import org.eclipse.aether.spi.connector.transport.http.HttpTransporter;
import org.eclipse.aether.util.ConfigUtils;

/**
 * Transporter for OCI repositories.
 */
public class OciTransport extends AbstractTransporter implements HttpTransporter {

    /**
     * The repository this transporter is for.
     */
    private final RemoteRepository repository;

    private final Registry registry;

    /**
     * Creates a new OCI transporter.
     * @param repository The repository this transporter is for.
     */
    public OciTransport(RepositorySystemSession session, RemoteRepository repository) {
        this.repository = repository;
        final String httpsSecurityMode = ConfigUtils.getString(
                session,
                ConfigurationProperties.HTTPS_SECURITY_MODE_DEFAULT,
                ConfigurationProperties.HTTPS_SECURITY_MODE + "." + repository.getId(),
                ConfigurationProperties.HTTPS_SECURITY_MODE);
        final boolean insecure = ConfigurationProperties.HTTPS_SECURITY_MODE_INSECURE.equals(httpsSecurityMode);
        this.registry = createRegistry(session, repository, insecure);
    }

    /**
     * Creates a new OCI Registry client
     * @param session The repository system session
     * @param repository The remote repository
     * @param insecure Whether to allow insecure connections
     * @return The OCI Registry client
     */
    private static Registry createRegistry(RepositorySystemSession session, RemoteRepository repository, boolean insecure) {
        Registry.Builder registryBuilder = Registry.Builder.builder();

        // Set insecure flag
        registryBuilder.withInsecure(insecure);

        try (AuthenticationContext repoAuthContext = AuthenticationContext.forRepository(session, repository)) {
            if (repoAuthContext != null) {
                String username = repoAuthContext.get(AuthenticationContext.USERNAME);
                String password = repoAuthContext.get(AuthenticationContext.PASSWORD);
                registryBuilder.withAuthProvider(new UsernamePasswordProvider(username, password));
            }
        }

        return registryBuilder.build();
    }

    @Override
    protected void implPeek(PeekTask task) throws Exception {

    }

    @Override
    protected void implGet(GetTask task) throws Exception {

    }

    @Override
    protected void implPut(PutTask task) throws Exception {

    }

    @Override
    protected void implClose() {

    }

    @Override
    public int classify(Throwable error) {
        return 0;
    }
}