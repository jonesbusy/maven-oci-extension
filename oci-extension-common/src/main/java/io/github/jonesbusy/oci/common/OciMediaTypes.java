package io.github.jonesbusy.oci.common;

import java.util.Locale;
import java.util.Map;

/**
 * Explicit media-type mapping per Maven artifact file extension, replacing extension-sniffing
 * fallback-to-octet-stream logic.
 */
public final class OciMediaTypes {

    private static final String DEFAULT_MEDIA_TYPE = "application/octet-stream";

    private static final Map<String, String> BY_EXTENSION = Map.of(
            "pom", "application/vnd.maven.pom+xml",
            "jar", "application/java-archive",
            "war", "application/vnd.maven.war",
            "ear", "application/vnd.maven.ear",
            "module", "application/vnd.maven.module+json",
            "xml", "application/xml");

    private OciMediaTypes() {}

    public static String forExtension(String extension) {
        if (extension == null || extension.isEmpty()) {
            return DEFAULT_MEDIA_TYPE;
        }
        return BY_EXTENSION.getOrDefault(extension.toLowerCase(Locale.ROOT), DEFAULT_MEDIA_TYPE);
    }
}
