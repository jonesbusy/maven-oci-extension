package io.github.jonesbusy.connector;

import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.spi.connector.RepositoryConnector;
import org.eclipse.aether.spi.connector.RepositoryConnectorFactory;
import org.eclipse.aether.transfer.NoRepositoryConnectorException;

import javax.inject.Named;

/**
 * Factory for {@link OciRepositoryConnector}, handling the {@code oci} and {@code oci+http}
 * (insecure/plain-HTTP) repository protocols. Handles both resolve (get) and publish (put) for
 * these protocols, replacing a separate Transporter/RepositoryLayout pair: a connector receives
 * batched artifact/metadata uploads, which is what lets publish assemble one OCI manifest per
 * Maven module version instead of pushing one file at a time.
 */
@Named("oci")
public final class OciRepositoryConnectorFactory implements RepositoryConnectorFactory {

    @Override
    public RepositoryConnector newInstance(RepositorySystemSession session, RemoteRepository repository)
            throws NoRepositoryConnectorException {
        String protocol = repository.getProtocol();
        if (!"oci".equals(protocol) && !"oci+http".equals(protocol)) {
            throw new NoRepositoryConnectorException(repository, "Unsupported repository protocol: " + protocol);
        }
        return new OciRepositoryConnector(session, repository);
    }

    @Override
    public float getPriority() {
        return 10.0f;
    }
}
