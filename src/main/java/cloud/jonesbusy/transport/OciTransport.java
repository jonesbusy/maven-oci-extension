package cloud.jonesbusy.transport;

import land.oras.ContainerRef;
import land.oras.Error;
import land.oras.Registry;
import land.oras.OrasException;
import land.oras.auth.UsernamePasswordProvider;
import land.oras.utils.JsonUtils;
import org.codehaus.plexus.logging.Logger;
import org.codehaus.plexus.logging.console.ConsoleLogger;
import org.eclipse.aether.ConfigurationProperties;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.repository.AuthenticationContext;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.spi.connector.transport.AbstractTransporter;
import org.eclipse.aether.spi.connector.transport.GetTask;
import org.eclipse.aether.spi.connector.transport.PeekTask;
import org.eclipse.aether.spi.connector.transport.PutTask;
import org.eclipse.aether.spi.connector.transport.http.HttpTransporter;
import org.eclipse.aether.spi.connector.transport.http.HttpTransporterException;
import org.eclipse.aether.transfer.NoTransporterException;
import org.eclipse.aether.util.ConfigUtils;
import org.eclipse.aether.util.FileUtils;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Transporter for OCI repositories.
 */
public class OciTransport extends AbstractTransporter implements HttpTransporter {

    private static final org.slf4j.Logger log = LoggerFactory.getLogger(OciTransport.class);
    /**
     * The repository this transporter is for.
     */
    private final RemoteRepository repository;
    /**
     * The OCI Registry client
     */
    private final Registry registry;
    private final Logger logger = new ConsoleLogger(Logger.LEVEL_DEBUG, "OciTransport");

    private final URI baseUri;

    /**
     * Creates a new OCI transporter.
     *
     * @param repository The repository this transporter is for.
     */
    public OciTransport(RepositorySystemSession session, RemoteRepository repository) throws NoTransporterException  {
        this.repository = repository;
        final String httpsSecurityMode = ConfigUtils.getString(
                session,
                ConfigurationProperties.HTTPS_SECURITY_MODE_DEFAULT,
                ConfigurationProperties.HTTPS_SECURITY_MODE + "." + repository.getId(),
                ConfigurationProperties.HTTPS_SECURITY_MODE);
        final boolean insecure = ConfigurationProperties.HTTPS_SECURITY_MODE_INSECURE.equals(httpsSecurityMode);
        this.registry = createRegistry(session, repository, insecure);

        // We omit the scheme for the container ref
        try {
            URI uri = new URI(repository.getUrl()).parseServerAuthority();
            String path = uri.getPath();
            this.baseUri = URI.create(uri.getRawAuthority() + path);
            logger.info("Created OCI transporter with base URI " + baseUri);
        }
        catch (URISyntaxException e) {
            throw new NoTransporterException(repository, e.getMessage(), e);
        }
    }

    /**
     * Creates a new OCI Registry client
     *
     * @param session    The repository system session
     * @param repository The remote repository
     * @param insecure   Whether to allow insecure connections
     * @return The OCI Registry client
     */
    private static Registry createRegistry(RepositorySystemSession session, RemoteRepository repository, boolean insecure) {
        Registry.Builder registryBuilder = Registry.Builder.builder();

        // Set insecure flag
        registryBuilder.withInsecure(true);

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
        logger.debug("Peeking " + task.getLocation());
    }

    @Override
    protected void implGet(GetTask task) throws HttpTransporterException, IOException {
        logger.debug("Getting " + task.getLocation());
        Path dataPath = task.getDataPath();
        if (dataPath.toFile().isFile()) {
            logger.debug("File already exists at " + dataPath);
        }
        else if (dataPath.toFile().isDirectory()) {
            logger.debug("Directory already exists at " + dataPath);
        }
        else {
            logger.debug("Creating directory at " + dataPath);
            Files.createDirectories(dataPath);
        }
        String containerRef = "%s/%s".formatted(baseUri, task.getLocation());
        logger.debug("Getting artifact from " + containerRef + " to " + dataPath);
        try {
            registry.pullArtifact(ContainerRef.parse(containerRef), dataPath);
        }
        // Correctly return the HTTP status code with HttpTransporterException
        catch(OrasException e) {
            logger.debug("Failed to get artifact from " + containerRef, e);
            Error error = e.getError();
            if (error != null) {
                logger.debug("Error: " + JsonUtils.toJson(error));
            }
            throw new HttpTransporterException(e.getStatusCode());
        }
    }

    @Override
    protected void implPut(PutTask task) throws Exception {
        logger.debug("Putting " + task.getLocation());
        Path dataPath = task.getDataPath();
        if (dataPath == null) {
            throw new IllegalArgumentException("Data path is null");
        }
        String containerRef = "%s/%s".formatted(baseUri, task.getLocation());
        try (FileUtils.TempFile tempFile = FileUtils.newTempFile()) {
            try {
                utilPut(task, Files.newOutputStream(tempFile.getPath()), true);
                registry.pushArtifact(ContainerRef.parse(containerRef), tempFile.getPath());
            }
            catch (OrasException e) {
                logger.debug("Failed to put artifact to " + containerRef, e);
                Error error = e.getError();
                if (error != null) {
                    logger.debug("Error: " + JsonUtils.toJson(error));
                }
                throw new HttpTransporterException(e.getStatusCode());
            }
        }

    }

    @Override
    protected void implClose() {
        logger.debug("Closing transporter for repository " + repository.getId());
    }

    @Override
    public int classify(Throwable error) {
        if (error instanceof HttpTransporterException
                && ((HttpTransporterException) error).getStatusCode() == 404) {
            logger.debug("Artifact not found");
            return ERROR_NOT_FOUND;
        }
        logger.error("Error during transport", error);
        return ERROR_OTHER;
    }
}