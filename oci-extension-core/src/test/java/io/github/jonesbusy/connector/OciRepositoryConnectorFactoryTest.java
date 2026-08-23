package io.github.jonesbusy.connector;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.spi.connector.RepositoryConnector;
import org.eclipse.aether.transfer.NoRepositoryConnectorException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class OciRepositoryConnectorFactoryTest {

    private final OciRepositoryConnectorFactory factory = new OciRepositoryConnectorFactory();

    @ParameterizedTest
    @ValueSource(strings = {"oci", "oci+http"})
    void acceptsOciProtocols(String protocol) throws NoRepositoryConnectorException {
        RemoteRepository repository =
                new RemoteRepository.Builder("test", "default", protocol + "://localhost:5000/ns").build();

        RepositoryConnector connector = factory.newInstance(null, repository);

        assertInstanceOf(OciRepositoryConnector.class, connector);
        connector.close();
    }

    @Test
    void rejectsOtherProtocols() {
        RemoteRepository repository =
                new RemoteRepository.Builder("test", "default", "https://localhost:5000/ns").build();

        assertThrows(NoRepositoryConnectorException.class, () -> factory.newInstance(null, repository));
    }
}
