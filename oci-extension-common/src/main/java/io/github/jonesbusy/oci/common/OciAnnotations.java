package io.github.jonesbusy.oci.common;

import land.oras.utils.Const;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Custom annotation keys recorded on every layer/manifest we push, so the resolve side can
 * identify which referrer/layer is which Maven file by annotation rather than by guessing from
 * a filename or picking "the first layer with a title".
 */
public final class OciAnnotations {

    private static final String PREFIX = "io.github.jonesbusy.oci.";

    public static final String GROUP_ID = PREFIX + "groupId";
    public static final String ARTIFACT_ID = PREFIX + "artifactId";
    public static final String VERSION = PREFIX + "version";
    public static final String CLASSIFIER = PREFIX + "classifier";
    public static final String EXTENSION = PREFIX + "extension";

    private OciAnnotations() {}

    /**
     * Builds the annotation set for the layer/manifest holding {@code coordinates}' content, whose
     * file is named {@code fileName} (recorded under the standard OCI title annotation).
     */
    public static Map<String, String> forArtifact(OciCoordinates coordinates, String fileName) {
        Map<String, String> annotations = new LinkedHashMap<>();
        annotations.put(Const.ANNOTATION_TITLE, fileName);
        annotations.put(GROUP_ID, coordinates.groupId());
        annotations.put(ARTIFACT_ID, coordinates.artifactId());
        annotations.put(VERSION, coordinates.version());
        if (coordinates.hasClassifier()) {
            annotations.put(CLASSIFIER, coordinates.classifier());
        }
        annotations.put(EXTENSION, coordinates.extension());
        return annotations;
    }

    /**
     * @return {@code true} if {@code annotations} (as produced by {@link #forArtifact}) was recorded
     * for the given classifier (empty/{@code null} meaning "no classifier").
     */
    public static boolean matchesClassifier(Map<String, String> annotations, String classifier) {
        String actual = annotations.get(CLASSIFIER);
        if (classifier == null || classifier.isEmpty()) {
            return actual == null || actual.isEmpty();
        }
        return classifier.equals(actual);
    }

    /**
     * @return {@code true} if {@code annotations} was recorded for the given file extension.
     */
    public static boolean matchesExtension(Map<String, String> annotations, String extension) {
        return extension != null && extension.equals(annotations.get(EXTENSION));
    }
}
