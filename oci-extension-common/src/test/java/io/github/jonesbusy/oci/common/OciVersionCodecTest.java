package io.github.jonesbusy.oci.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class OciVersionCodecTest {

    @ParameterizedTest
    @ValueSource(
            strings = {
                "1.0.0",
                "1.0.0-SNAPSHOT",
                "1.0",
                "2023.1.1",
                "1.0.0+20230101",
                "1.0.0+build.1",
                "1.0.0_fix",
                "1.0.0__already_doubled",
                "1.0.0+a+b+c",
                "v1_2_3",
                "1.0.0+",
                "+1.0.0",
                "1.0.0 with spaces",
                "1.0.0:colon",
                "1.0.0/slash",
                "release/2024",
                "1.0.0~tilde",
            })
    void roundTrips(String version) {
        String tag = OciVersionCodec.encode(version);
        assertEquals(version, OciVersionCodec.decode(tag));
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "1.0.0",
                "1.0.0-SNAPSHOT",
                "1.0.0+20230101",
                "1.0.0_fix",
                "+1.0.0",
                "1.0.0 with spaces",
            })
    void producesValidOciTagSyntax(String version) {
        String tag = OciVersionCodec.encode(version);
        assertTrue(tag.matches("[a-zA-Z0-9_][a-zA-Z0-9._-]{0,127}"), "not a valid OCI tag: " + tag);
    }

    @Test
    void encodeRejectsEmptyVersion() {
        assertThrows(IllegalArgumentException.class, () -> OciVersionCodec.encode(""));
    }

    @Test
    void encodeIsDeterministic() {
        assertEquals(OciVersionCodec.encode("1.0.0+build"), OciVersionCodec.encode("1.0.0+build"));
    }

    @Test
    void decodeRejectsTruncatedEscape() {
        assertThrows(IllegalArgumentException.class, () -> OciVersionCodec.decode("1.0.0_00"));
    }

    @Test
    void decodeRejectsNonHexEscape() {
        assertThrows(IllegalArgumentException.class, () -> OciVersionCodec.decode("1.0.0_zzzz"));
    }

    @Test
    void literalEscapeLookingInputDoesNotCollideWithARealEscape() {
        String tagForPlus = OciVersionCodec.encode("1.0.0+");
        String tagForLiteral = OciVersionCodec.encode("1.0.0_002b");
        assertEquals("1.0.0_002b", tagForPlus);
        assertEquals("1.0.0__002b", tagForLiteral);
        assertNotEquals(tagForPlus, tagForLiteral);
        assertEquals("1.0.0+", OciVersionCodec.decode(tagForPlus));
        assertEquals("1.0.0_002b", OciVersionCodec.decode(tagForLiteral));
    }
}
