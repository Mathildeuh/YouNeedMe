package fr.mathildeuh.youneedme.modules.scoreboard;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ScoreboardModuleTest {

    @Test
    void scoreboardNeverStartsOnFolia() {
        assertFalse(ScoreboardModule.shouldStart(true, false, true));
    }

    @Test
    void scoreboardStartsWhenSupportedAndConfigured() {
        assertTrue(ScoreboardModule.shouldStart(false, false, true));
    }

    @Test
    void scoreboardDefersToTab() {
        assertFalse(ScoreboardModule.shouldStart(false, true, true));
    }
}
