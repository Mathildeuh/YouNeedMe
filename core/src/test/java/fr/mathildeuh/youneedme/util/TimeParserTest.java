package fr.mathildeuh.youneedme.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class TimeParserTest {

    @ParameterizedTest
    @CsvSource({
        "1d, 86400000",
        "2h, 7200000",
        "30m, 1800000",
        "45s, 45000",
        "1d2h30m, 95400000",
    })
    void parsesEachDurationSegment(String input, long expectedMillis) {
        assertEquals(expectedMillis, TimeParser.parseMillis(input));
    }

    @ParameterizedTest
    @CsvSource({"perm", "permanent", "PERM", "-1"})
    void treatsCommonPermanentAliasesAsPermanent(String input) {
        assertTrue(TimeParser.isPermanent(input));
        assertNull(TimeParser.parseMillis(input));
    }

    @ParameterizedTest
    @CsvSource({"abc", "1x", "1d 2h", "garbage123"})
    void rejectsGarbageInput(String input) {
        assertFalse(TimeParser.isPermanent(input));
        assertNull(TimeParser.parseMillis(input));
        assertFalse(TimeParser.isValid(input));
    }

    @Test
    void formatsBackIntoACompactString() {
        assertEquals("1d2h30m", TimeParser.format(95_400_000L));
        assertEquals("45s", TimeParser.format(45_000L));
        assertEquals("0s", TimeParser.format(0L));
        assertEquals("0s", TimeParser.format(-1L));
    }
}
