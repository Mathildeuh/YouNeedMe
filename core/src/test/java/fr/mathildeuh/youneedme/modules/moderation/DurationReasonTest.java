package fr.mathildeuh.youneedme.modules.moderation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class DurationReasonTest {

    @Test
    void noArgumentsFallsBackToTheDefaultReasonWithNoDuration() {
        DurationReason result = DurationReason.parse(new String[0], "Breaking the rules");

        assertNull(result.durationMillis());
        assertEquals("Breaking the rules", result.reason());
    }

    @Test
    void aLeadingDurationIsParsedAndTheRestBecomesTheReason() {
        DurationReason result =
                DurationReason.parse(new String[] {"1d2h", "Griefing", "spawn"}, "default");

        assertEquals(26 * 3_600_000L, result.durationMillis());
        assertEquals("Griefing spawn", result.reason());
    }

    @Test
    void aPermanentMarkerLeavesTheDurationNull() {
        DurationReason result = DurationReason.parse(new String[] {"perm", "Cheating"}, "default");

        assertNull(result.durationMillis());
        assertEquals("Cheating", result.reason());
    }

    @Test
    void aNonDurationFirstArgumentIsTreatedAsPartOfTheReason() {
        DurationReason result = DurationReason.parse(new String[] {"Being", "rude"}, "default");

        assertNull(result.durationMillis());
        assertEquals("Being rude", result.reason());
    }

    @Test
    void aDurationWithNoFollowingWordsUsesTheDefaultReason() {
        DurationReason result = DurationReason.parse(new String[] {"30m"}, "default reason");

        assertEquals(1_800_000L, result.durationMillis());
        assertEquals("default reason", result.reason());
    }
}
