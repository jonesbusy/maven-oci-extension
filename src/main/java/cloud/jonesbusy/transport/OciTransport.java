package cloud.jonesbusy.transport;

import land.oras.*;
import land.oras.exception.Error;
import land.oras.exception.OrasException;
import land.oras.utils.Const;
import land.oras.utils.JsonUtils;
import org.codehaus.plexus.logging.Logger;
import org.codehaus.plexus.logging.console.ConsoleLogger;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.spi.connector.transport.AbstractTransporter;
import org.eclipse.aether.spi.connector.transport.GetTask;
import org.eclipse.aether.spi.connector.transport.PeekTask;
import org.eclipse.aether.spi.connector.transport.PutTask;
import org.eclipse.aether.spi.connector.transport.http.HttpTransporter;
import org.eclipse.aether.spi.connector.transport.http.HttpTransporterException;
import org.eclipse.aether.transfer.NoTransporterException;
import org.eclipse.aether.util.FileUtils;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.CopyOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;

/**
 * Transporter for OCI repositories.
 */
public class OciTransport extends AbstractTransporter implements HttpTransporter {

    /**
     * The logger.
     */
    private static final Logger LOG = new ConsoleLogger(Logger.LEVEL_DEBUG, OciTransport.class.getName());

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
        final boolean insecure = repository.getProtocol().equals("oci+http");
        logger.info("Creating OCI transporter for repository " + repository.getId());
        logger.info("Insecure: " + insecure);
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
        return Registry.Builder.builder().withInsecure(insecure).defaults().build();
    }

    @Override
    protected void implPeek(PeekTask task) throws HttpTransporterException, IOException {
        logger.debug("Peeking " + task.getLocation());
        String containerRef = "%s/%s".formatted(baseUri, task.getLocation());
        try {
            Manifest manifest = registry.getManifest(ContainerRef.parse(containerRef));
        }
        catch(OrasException e) {
            logger.debug("Failed to peek artifact at " + containerRef, e);
            Error error = e.getError();
            if (error != null) {
                logger.debug("Error: " + JsonUtils.toJson(error));
            }
            throw new HttpTransporterException(e.getStatusCode());
        }
    }

    @Override
    protected void implGet(GetTask task) throws Exception {
        logger.debug("Getting " + task.getLocation());
        Path dataPath = task.getDataPath();
        logger.debug("dataPath: " + dataPath);
        logger.debug("parent: " + dataPath.getParent());
        String container = "%s/%s".formatted(baseUri, task.getLocation());
        ContainerRef containerRef = ContainerRef.parse(container);
        try {
            logger.debug("Getting manifest");
            Manifest manifest = registry.getManifest(containerRef);
            logger.debug(JsonUtils.toJson(manifest));
            Layer l = manifest.getLayers().stream().filter(layer -> layer.getAnnotations().containsKey(Const.ANNOTATION_TITLE)).findFirst().get();
            logger.debug("Getting blob");
            InputStream response = registry.fetchBlob(containerRef.withDigest(l.getDigest()));
            final Path dataFile = task.getDataPath();
            if (dataFile == null) {
                logger.debug("Data file is null");
                try (InputStream is = response) {
                    utilGet(task, is, true, -1, false);
                    logger.debug("First untilGet");
                }
            }
            try (FileUtils.CollocatedTempFile tempFile = FileUtils.newTempFile(dataFile)) {
                task.setDataPath(tempFile.getPath(), false);
                if (Files.isRegularFile(dataFile)) {
                    try (InputStream inputStream = new BufferedInputStream(Files.newInputStream(dataFile))) {
                        Files.copy(inputStream, tempFile.getPath(), StandardCopyOption.REPLACE_EXISTING);
                    }
                }
                try (InputStream is = response) {
                    utilGet(task, is, true, -1, false);
                    logger.debug("Second untilGet");
                }
                tempFile.move();
                logger.debug("Getting artifact from " + containerRef + " to " + dataPath);
                // Correctly return the HTTP status code with HttpTransporterException

            }
            finally {
                task.setDataPath(dataFile);
            }
        }
        catch(OrasException e) {
            if (e.getStatusCode() != 404) {
                logger.debug("Failed to get artifact to " + containerRef, e);
            }
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
        logger.debug("Claas: " + task.getClass());
        String containerRef = "%s/%s".formatted(baseUri, task.getLocation());
        //try (FileUtils.TempFile tempFile = FileUtils.newTempFile()) {
            try {
                //utilPut(task, Files.newOutputStream(tempFile.getPath()), true);
                Annotations annotations = Annotations.ofManifest(Map.of(Const.ANNOTATION_TITLE, dataPath.getFileName().toString()));
                registry.pushArtifact(ContainerRef.parse(containerRef), ArtifactType.from("application/vnd.maven.artifact.v1"), annotations, LocalPath.of(dataPath));
                logger.debug("Uploaded artifact from " + dataPath + " to " + containerRef);
            }
            catch (OrasException e) {
                if (e.getStatusCode() != 404) {
                    logger.debug("Failed to put artifact to " + containerRef, e);
                }
                Error error = e.getError();
                if (error != null) {
                    logger.debug("Error: " + JsonUtils.toJson(error));
                }
                throw new HttpTransporterException(e.getStatusCode());
            }
        //}

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