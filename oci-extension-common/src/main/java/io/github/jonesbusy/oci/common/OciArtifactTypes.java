package io.github.jonesbusy.oci.common;

/**
 * OCI manifest-level {@code artifactType} values used for the two kinds of manifest we push: the
 * primary manifest (pom + main artifact, tagged with the version) and secondary referrer manifests
 * (classified artifacts such as sources/javadoc, attached to the primary manifest).
 */
public final class OciArtifactTypes {

    public static final String PRIMARY = "application/vnd.maven.artifact.v1";
    public static final String SECONDARY = "application/vnd.maven.artifact.attachment.v1";

    private OciArtifactTypes() {}
}
