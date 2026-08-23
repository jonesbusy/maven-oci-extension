package cloud.jonesbusy.oci.common;

import land.oras.utils.Const;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OciAnnotationsTest {

    @Test
    void buildsAnnotationsForArtifactWithoutClassifier() {
        OciCoordinates coordinates = new OciCoordinates("com.example", "my-app", "1.0.0", null, "jar");
        Map<String, String> annotations = OciAnnotations.forArtifact(coordinates, "my-app-1.0.0.jar");

        assertEquals("my-app-1.0.0.jar", annotations.get(Const.ANNOTATION_TITLE));
        assertEquals("com.example", annotations.get(OciAnnotations.GROUP_ID));
        assertEquals("my-app", annotations.get(OciAnnotations.ARTIFACT_ID));
        assertEquals("1.0.0", annotations.get(OciAnnotations.VERSION));
        assertEquals("jar", annotations.get(OciAnnotations.EXTENSION));
        assertFalse(annotations.containsKey(OciAnnotations.CLASSIFIER));
    }

    @Test
    void buildsAnnotationsForClassifiedArtifact() {
        OciCoordinates coordinates = new OciCoordinates("com.example", "my-app", "1.0.0", "sources", "jar");
        Map<String, String> annotations = OciAnnotations.forArtifact(coordinates, "my-app-1.0.0-sources.jar");

        assertEquals("sources", annotations.get(OciAnnotations.CLASSIFIER));
    }

    @Test
    void matchesClassifierTreatsMissingAndEmptyAsNoClassifier() {
        OciCoordinates coordinates = new OciCoordinates("com.example", "my-app", "1.0.0", null, "jar");
        Map<String, String> annotations = OciAnnotations.forArtifact(coordinates, "my-app-1.0.0.jar");

        assertTrue(OciAnnotations.matchesClassifier(annotations, null));
        assertTrue(OciAnnotations.matchesClassifier(annotations, ""));
        assertFalse(OciAnnotations.matchesClassifier(annotations, "sources"));
    }

    @Test
    void matchesClassifierMatchesExactClassifier() {
        OciCoordinates coordinates = new OciCoordinates("com.example", "my-app", "1.0.0", "sources", "jar");
        Map<String, String> annotations = OciAnnotations.forArtifact(coordinates, "my-app-1.0.0-sources.jar");

        assertTrue(OciAnnotations.matchesClassifier(annotations, "sources"));
        assertFalse(OciAnnotations.matchesClassifier(annotations, "javadoc"));
        assertFalse(OciAnnotations.matchesClassifier(annotations, null));
    }

    @Test
    void matchesExtension() {
        OciCoordinates coordinates = new OciCoordinates("com.example", "my-app", "1.0.0", null, "jar");
        Map<String, String> annotations = OciAnnotations.forArtifact(coordinates, "my-app-1.0.0.jar");

        assertTrue(OciAnnotations.matchesExtension(annotations, "jar"));
        assertFalse(OciAnnotations.matchesExtension(annotations, "pom"));
    }
}
