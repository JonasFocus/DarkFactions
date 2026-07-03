package com.darkfactions.storage;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Logger;

public class SaveQueue {

    private final ScheduledExecutorService executor;
    private final DataStore store;

    public SaveQueue(DataStore store, int threadCount) {
        this.store = store;
        this.executor = Executors.newScheduledThreadPool(threadCount, r -> {
            Thread t = new Thread(r, "DarkFactions-SaveQueue");
            t.setDaemon(true);
            return t;
        });
    }

    public void submit(Runnable task) {
        executor.submit(task);
    }

    public DataStore store() {
        return store;
    }

    /**
     * Blocks until all tasks submitted before this call finish, or times out.
     * Used during shutdown after synchronous saves to drain remaining async work.
     * Only a true barrier when the queue has a single worker thread (the sentinel
     * task could otherwise overtake an in-flight save on another worker), which is
     * how DarkFactions constructs it.
     */
    public void flushAndAwait(long timeout, TimeUnit unit) {
        try {
            executor.submit(() -> { }).get(timeout, unit);
        } catch (TimeoutException e) {
            Logger.getLogger("DarkFactions").warning(
                    "Save queue flush timed out after " + timeout + " " + unit);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            Logger.getLogger("DarkFactions").warning("Save queue flush interrupted");
        } catch (Exception e) {
            Logger.getLogger("DarkFactions").warning("Save queue flush failed: " + e.getMessage());
        }
    }

    public void shutdown() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}