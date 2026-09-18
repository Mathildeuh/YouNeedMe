package fr.mathildeuh.youneedme.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class CooldownsTest {

    private final UUID player = UUID.randomUUID();

    @Test
    void aFreshPlayerIsNeverOnCooldown() {
        Cooldowns cooldowns = new Cooldowns();
        assertFalse(cooldowns.isOnCooldown(player, "warp"));
        assertEquals(0, cooldowns.remainingMillis(player, "warp"));
    }

    @Test
    void settingACooldownMakesItActiveUntilItExpires() {
        Cooldowns cooldowns = new Cooldowns();
        cooldowns.set(player, "warp", 10_000);

        assertTrue(cooldowns.isOnCooldown(player, "warp"));
        assertTrue(cooldowns.remainingMillis(player, "warp") > 0);
    }

    @Test
    void cooldownsAreIndependentPerKey() {
        Cooldowns cooldowns = new Cooldowns();
        cooldowns.set(player, "warp", 10_000);

        assertFalse(cooldowns.isOnCooldown(player, "rtp"));
    }

    @Test
    void clearRemovesTheCooldownImmediately() {
        Cooldowns cooldowns = new Cooldowns();
        cooldowns.set(player, "warp", 10_000);
        cooldowns.clear(player, "warp");

        assertFalse(cooldowns.isOnCooldown(player, "warp"));
    }

    @Test
    void forgetPlayerClearsEveryCooldownForThatPlayer() {
        Cooldowns cooldowns = new Cooldowns();
        cooldowns.set(player, "warp", 10_000);
        cooldowns.set(player, "rtp", 10_000);

        cooldowns.forgetPlayer(player);

        assertFalse(cooldowns.isOnCooldown(player, "warp"));
        assertFalse(cooldowns.isOnCooldown(player, "rtp"));
    }
}
