package cloud.jonesbusy.oci.common;

import static java.util.Objects.requireNonNull;

/**
 * Reversible encoding between a Maven version and a valid OCI tag.
 * <p>
 * OCI tags are restricted to {@code [a-zA-Z0-9_][a-zA-Z0-9._-]{0,127}}, but Maven versions can
 * contain characters outside that set (notably {@code +}, used in build-metadata qualifiers).
 * Every literal {@code _} in the input is doubled ({@code __}) and every disallowed character is
 * escaped as {@code _} followed by its 4-digit lowercase hex UTF-16 code unit (e.g. {@code +} -&gt;
 * {@code _002b}), so decoding is unambiguous: a lone {@code _} is always followed by either another
 * {@code _} (a literal underscore) or exactly 4 hex digits (an escaped character).
 */
public final class OciVersionCodec {

    private static final int MAX_TAG_LENGTH = 128;

    private OciVersionCodec() {}

    public static String encode(String version) {
        requireNonNull(version, "version");
        if (version.isEmpty()) {
            throw new IllegalArgumentException("version must not be empty");
        }
        StringBuilder sb = new StringBuilder(version.length());
        for (int i = 0; i < version.length(); i++) {
            char c = version.charAt(i);
            if (c == '_') {
                sb.append("__");
            } else if (isAllowed(c)) {
                sb.append(c);
            } else {
                sb.append('_').append(String.format("%04x", (int) c));
            }
        }
        String tag = sb.toString();
        if (tag.length() > MAX_TAG_LENGTH) {
            throw new IllegalArgumentException(
                    "Encoded OCI tag for version '" + version + "' exceeds " + MAX_TAG_LENGTH + " characters");
        }
        return tag;
    }

    public static String decode(String tag) {
        requireNonNull(tag, "tag");
        StringBuilder sb = new StringBuilder(tag.length());
        int i = 0;
        while (i < tag.length()) {
            char c = tag.charAt(i);
            if (c != '_') {
                sb.append(c);
                i++;
                continue;
            }
            if (i + 1 < tag.length() && tag.charAt(i + 1) == '_') {
                sb.append('_');
                i += 2;
                continue;
            }
            if (i + 5 > tag.length()) {
                throw new IllegalArgumentException("Malformed OCI tag escape at index " + i + " in: " + tag);
            }
            String hex = tag.substring(i + 1, i + 5);
            try {
                sb.append((char) Integer.parseInt(hex, 16));
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Malformed OCI tag escape at index " + i + " in: " + tag, e);
            }
            i += 5;
        }
        return sb.toString();
    }

    private static boolean isAllowed(char c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') || c == '.' || c == '-';
    }
}
