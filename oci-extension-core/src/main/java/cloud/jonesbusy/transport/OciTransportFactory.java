package cloud.jonesbusy.transport;

import org.codehaus.plexus.logging.Logger;
import org.codehaus.plexus.logging.console.ConsoleLogger;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.spi.connector.transport.Transporter;
import org.eclipse.aether.spi.connector.transport.TransporterFactory;
import org.eclipse.aether.transfer.NoTransporterException;

import javax.inject.Inject;
import javax.inject.Named;

/**
 * Factory for creating OCI transporters.
 */
@Named("oci")
public final class OciTransportFactory implements TransporterFactory {

    /**
     * The logger.
     */
    private static final Logger LOG = new ConsoleLogger(Logger.LEVEL_DEBUG, OciTransport.class.getName());

    @Override
    public Transporter newInstance(RepositorySystemSession session, RemoteRepository repository) throws NoTransporterException {
        if ("oci".equals(repository.getProtocol()) || "oci+http".equals(repository.getProtocol())) {
            LOG.info("Creating OCI transporter for repository " + repository.getId());
            return new OciTransport(session, repository);
        }
        throw new NoTransporterException(repository, "Unsupported repository protocol: " + repository.getProtocol());
    }

    @Override
    public float getPriority() {
        return 10.0f;
    }

}
