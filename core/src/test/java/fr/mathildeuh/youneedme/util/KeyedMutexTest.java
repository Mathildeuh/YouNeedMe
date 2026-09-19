package fr.mathildeuh.youneedme.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class KeyedMutexTest {

    @Test
    void actionsForTheSameKeyNeverOverlap() throws InterruptedException {
        KeyedMutex<String> mutex = new KeyedMutex<>();
        List<String> events = new CopyOnWriteArrayList<>();
        try (ExecutorService executor = Executors.newFixedThreadPool(4)) {
            int taskCount = 20;
            CountDownLatch done = new CountDownLatch(taskCount);
            for (int i = 0; i < taskCount; i++) {
                int id = i;
                executor.submit(
                        () ->
                                mutex.runExclusive(
                                                "player",
                                                () ->
                                                        CompletableFuture.supplyAsync(
                                                                () -> {
                                                                    events.add("start-" + id);
                                                                    events.add("end-" + id);
                                                                    return null;
                                                                },
                                                                executor))
                                        .whenComplete((v, ex) -> done.countDown()));
            }
            assertTrue(done.await(10, TimeUnit.SECONDS), "all tasks should finish within 10s");
        }

        // Since every "start-N"/"end-N" pair for the same task is added back-to-back inside one
        // exclusive action, an interleaved start from a second task appearing before the first
        // task's end would prove two actions for the same key ran concurrently.
        assertEquals(40, events.size());
        for (int i = 0; i < events.size(); i += 2) {
            String start = events.get(i);
            String end = events.get(i + 1);
            assertEquals(start.substring("start-".length()), end.substring("end-".length()));
        }
    }

    @Test
    void differentKeysRunIndependently() {
        KeyedMutex<String> mutex = new KeyedMutex<>();
        CompletableFuture<String> a =
                mutex.runExclusive("a", () -> CompletableFuture.completedFuture("a-done"));
        CompletableFuture<String> b =
                mutex.runExclusive("b", () -> CompletableFuture.completedFuture("b-done"));

        assertEquals("a-done", a.join());
        assertEquals("b-done", b.join());
    }

    @Test
    void anExceptionInOneActionDoesNotBlockTheNextOneForTheSameKey() {
        KeyedMutex<String> mutex = new KeyedMutex<>();
        CompletableFuture<String> failing =
                mutex.runExclusive(
                        "player",
                        () -> CompletableFuture.failedFuture(new IllegalStateException("boom")));
        CompletableFuture<String> next =
                mutex.runExclusive("player", () -> CompletableFuture.completedFuture("ok"));

        assertTrue(failing.isCompletedExceptionally());
        assertEquals("ok", next.join());
    }
}
