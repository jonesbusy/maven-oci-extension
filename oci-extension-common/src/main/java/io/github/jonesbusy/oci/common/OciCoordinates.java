package io.github.jonesbusy.oci.common;

import static java.util.Objects.requireNonNull;

import java.util.Objects;

/**
 * Plain (Aether-free) representation of the Maven coordinates of one artifact file,
 * used as the common currency between the resolve and publish sides of the OCI mapping.
 */
public final class OciCoordinates {

    private final String groupId;
    private final String artifactId;
    private final String version;
    private final String classifier;
    private final String extension;

    public OciCoordinates(String groupId, String artifactId, String version, String classifier, String extension) {
        this.groupId = requireNonNull(groupId, "groupId");
        this.artifactId = requireNonNull(artifactId, "artifactId");
        this.version = requireNonNull(version, "version");
        this.classifier = classifier == null ? "" : classifier;
        this.extension = requireNonNull(extension, "extension");
    }

    public String groupId() {
        return groupId;
    }

    public String artifactId() {
        return artifactId;
    }

    public String version() {
        return version;
    }

    /** Never {@code null}; empty string means "no classifier". */
    public String classifier() {
        return classifier;
    }

    public boolean hasClassifier() {
        return !classifier.isEmpty();
    }

    public String extension() {
        return extension;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof OciCoordinates that)) {
            return false;
        }
        return groupId.equals(that.groupId)
                && artifactId.equals(that.artifactId)
                && version.equals(that.version)
                && classifier.equals(that.classifier)
                && extension.equals(that.extension);
    }

    @Override
    public int hashCode() {
        return Objects.hash(groupId, artifactId, version, classifier, extension);
    }

    @Override
    public String toString() {
        return groupId + ":" + artifactId + ":" + version + (hasClassifier() ? ":" + classifier : "") + "@" + extension;
    }
}
