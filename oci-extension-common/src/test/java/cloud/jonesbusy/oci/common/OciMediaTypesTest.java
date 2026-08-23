package cloud.jonesbusy.oci.common;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OciMediaTypesTest {

    @Test
    void mapsKnownExtensions() {
        assertEquals("application/vnd.maven.pom+xml", OciMediaTypes.forExtension("pom"));
        assertEquals("application/java-archive", OciMediaTypes.forExtension("jar"));
    }

    @Test
    void isCaseInsensitive() {
        assertEquals("application/java-archive", OciMediaTypes.forExtension("JAR"));
    }

    @Test
    void fallsBackToOctetStreamForUnknownExtension() {
        assertEquals("application/octet-stream", OciMediaTypes.forExtension("unknownext"));
    }

    @Test
    void fallsBackToOctetStreamForNullOrEmpty() {
        assertEquals("application/octet-stream", OciMediaTypes.forExtension(null));
        assertEquals("application/octet-stream", OciMediaTypes.forExtension(""));
    }
}
