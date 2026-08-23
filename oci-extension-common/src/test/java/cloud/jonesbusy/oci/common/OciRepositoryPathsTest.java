package cloud.jonesbusy.oci.common;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OciRepositoryPathsTest {

    @Test
    void buildsRepositoryPathFromGroupAndArtifactId() {
        assertEquals("com/example/my-app", OciRepositoryPaths.repository("com.example", "my-app"));
    }

    @Test
    void lowercasesMixedCaseCoordinates() {
        assertEquals("com/mycompany/myapp", OciRepositoryPaths.repository("com.MyCompany", "MyApp"));
    }

    @Test
    void rejectsEmptyGroupId() {
        assertThrows(IllegalArgumentException.class, () -> OciRepositoryPaths.repository("", "my-app"));
    }

    @Test
    void rejectsEmptyArtifactId() {
        assertThrows(IllegalArgumentException.class, () -> OciRepositoryPaths.repository("com.example", ""));
    }

    @Test
    void tagDelegatesToVersionCodec() {
        assertEquals(OciVersionCodec.encode("1.0.0+build"), OciRepositoryPaths.tag("1.0.0+build"));
    }
}
