package cloud.jonesbusy.oci.common;

import java.util.Locale;
import java.util.regex.Pattern;

import static java.util.Objects.requireNonNull;

/**
 * Maps Maven groupId/artifactId/version onto an OCI repository path + tag: one OCI repository per
 * {@code groupId:artifactId}, one tag per version.
 */
public final class OciRepositoryPaths {

    // OCI distribution spec repository name component grammar.
    private static final Pattern REPOSITORY_SEGMENT = Pattern.compile("[a-z0-9]+((\\.|_|__|-+)[a-z0-9]+)*");

    private OciRepositoryPaths() {}

    /**
     * @return the OCI repository path for the given groupId/artifactId, e.g. {@code com/example/my-app}.
     */
    public static String repository(String groupId, String artifactId) {
        requireNonNull(groupId, "groupId");
        requireNonNull(artifactId, "artifactId");
        if (groupId.isEmpty()) {
            throw new IllegalArgumentException("groupId must not be empty");
        }
        if (artifactId.isEmpty()) {
            throw new IllegalArgumentException("artifactId must not be empty");
        }
        String path = groupId.toLowerCase(Locale.ROOT).replace('.', '/') + "/" + artifactId.toLowerCase(Locale.ROOT);
        for (String segment : path.split("/")) {
            if (!REPOSITORY_SEGMENT.matcher(segment).matches()) {
                throw new IllegalArgumentException("groupId '" + groupId + "' / artifactId '" + artifactId
                        + "' produces an invalid OCI repository path segment: '" + segment + "'");
            }
        }
        return path;
    }

    /**
     * @return the OCI tag for the given Maven version.
     */
    public static String tag(String version) {
        return OciVersionCodec.encode(version);
    }
}
