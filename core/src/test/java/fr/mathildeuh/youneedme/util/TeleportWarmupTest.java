package fr.mathildeuh.youneedme.util;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import fr.mathildeuh.youneedme.scheduler.impl.BukkitSchedulerAdapter;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.plugin.PluginMock;

class TeleportWarmupTest {

    private ServerMock server;
    private PluginMock plugin;
    private TeleportWarmup warmup;
    private BukkitSchedulerAdapter scheduler;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        warmup = new TeleportWarmup();
        server.getPluginManager().registerEvents(warmup, plugin);
        scheduler = new BukkitSchedulerAdapter(plugin);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void completesOnceTheWarmupElapses() {
        PlayerMock player = server.addPlayer();
        AtomicBoolean completed = new AtomicBoolean();
        AtomicBoolean cancelled = new AtomicBoolean();

        warmup.start(player, 1, scheduler, () -> completed.set(true), () -> cancelled.set(true));
        assertTrue(warmup.hasPending(player.getUniqueId()));

        server.getScheduler().performTicks(21);

        assertTrue(completed.get(), "onComplete should have fired once the warmup elapsed");
        assertFalse(cancelled.get());
        assertFalse(warmup.hasPending(player.getUniqueId()));
    }

    @Test
    void cancellingBeforeCompletionRunsTheCancelCallbackInstead() {
        PlayerMock player = server.addPlayer();
        AtomicBoolean completed = new AtomicBoolean();
        AtomicBoolean cancelled = new AtomicBoolean();

        warmup.start(player, 5, scheduler, () -> completed.set(true), () -> cancelled.set(true));
        warmup.cancel(player.getUniqueId());

        assertFalse(warmup.hasPending(player.getUniqueId()));
        // cancel() itself only removes the pending state and stops the scheduled task - it is the
        // move/damage/quit listeners that actually invoke onCancelled, so neither callback should
        // have run from a direct cancel() call.
        assertFalse(completed.get());
        assertFalse(cancelled.get());
    }

    @Test
    void zeroSecondWarmupCompletesImmediately() {
        PlayerMock player = server.addPlayer();
        AtomicBoolean completed = new AtomicBoolean();

        warmup.start(player, 0, scheduler, () -> completed.set(true), () -> {});

        assertTrue(completed.get());
        assertFalse(warmup.hasPending(player.getUniqueId()));
    }
}
