package cloud.jonesbusy.connector;

import org.apache.maven.artifact.versioning.ComparableVersion;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Synthesizes a GA-level {@code maven-metadata.xml} document from an OCI repository's tag list, so
 * the registry's own tags stay the single source of truth for "what versions exist" rather than a
 * separately maintained metadata artifact.
 */
final class MavenMetadataXml {

    private static final DateTimeFormatter LAST_UPDATED_FORMAT =
            DateTimeFormatter.ofPattern("yyyyMMddHHmmss").withZone(ZoneOffset.UTC);

    private MavenMetadataXml() {}

    /**
     * @param versions decoded Maven versions (not OCI tags) present for this groupId:artifactId.
     * @return the {@code maven-metadata.xml} content, or empty if {@code versions} is empty.
     */
    static String build(String groupId, String artifactId, List<String> versions, Instant now) {
        List<String> sorted = new ArrayList<>(versions);
        sorted.sort((a, b) -> new ComparableVersion(a).compareTo(new ComparableVersion(b)));

        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        xml.append("<metadata>\n");
        xml.append("  <groupId>").append(escape(groupId)).append("</groupId>\n");
        xml.append("  <artifactId>").append(escape(artifactId)).append("</artifactId>\n");
        xml.append("  <versioning>\n");
        String latest = sorted.get(sorted.size() - 1);
        xml.append("    <latest>").append(escape(latest)).append("</latest>\n");
        String release = lastRelease(sorted);
        if (release != null) {
            xml.append("    <release>").append(escape(release)).append("</release>\n");
        }
        xml.append("    <versions>\n");
        for (String version : sorted) {
            xml.append("      <version>").append(escape(version)).append("</version>\n");
        }
        xml.append("    </versions>\n");
        xml.append("    <lastUpdated>").append(LAST_UPDATED_FORMAT.format(now)).append("</lastUpdated>\n");
        xml.append("  </versioning>\n");
        xml.append("</metadata>\n");
        return xml.toString();
    }

    private static String lastRelease(List<String> sortedVersions) {
        for (int i = sortedVersions.size() - 1; i >= 0; i--) {
            String version = sortedVersions.get(i);
            if (!version.endsWith("-SNAPSHOT")) {
                return version;
            }
        }
        return null;
    }

    private static String escape(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
