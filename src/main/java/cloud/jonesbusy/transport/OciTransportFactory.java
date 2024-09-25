package cloud.jonesbusy.transport;

import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.spi.connector.transport.Transporter;
import org.eclipse.aether.spi.connector.transport.TransporterFactory;
import org.eclipse.aether.transfer.NoTransporterException;

import javax.inject.Named;

/**
 * Factory for creating OCI transporters.
 */
@Named("oci")
public final class OciTransportFactory implements TransporterFactory {

    @Override
    public Transporter newInstance(RepositorySystemSession session, RemoteRepository repository) throws NoTransporterException {
        if ("oci".equals(repository.getProtocol())) {
            return new OciTransport(repository);
        }
        throw new NoTransporterException(repository, "Unsupported repository protocol: " + repository.getProtocol());
    }

    @Override
    public float getPriority() {
        return 10.0f;
    }

}
