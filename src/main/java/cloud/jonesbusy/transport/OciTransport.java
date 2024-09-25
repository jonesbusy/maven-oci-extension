package cloud.jonesbusy.transport;

import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.spi.connector.transport.AbstractTransporter;
import org.eclipse.aether.spi.connector.transport.GetTask;
import org.eclipse.aether.spi.connector.transport.PeekTask;
import org.eclipse.aether.spi.connector.transport.PutTask;
import org.eclipse.aether.spi.connector.transport.http.HttpTransporter;

/**
 * Transporter for OCI repositories.
 */
public class OciTransport extends AbstractTransporter implements HttpTransporter {

    /**
     * The repository this transporter is for.
     */
    private final RemoteRepository repository;

    /**
     * Creates a new OCI transporter.
     * @param repository The repository this transporter is for.
     */
    public OciTransport(RemoteRepository repository) {
        this.repository = repository;
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