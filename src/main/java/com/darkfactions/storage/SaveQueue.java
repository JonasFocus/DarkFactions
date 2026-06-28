package com.darkfactions.storage;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

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

    public void scheduleAtFixedRate(Runnable task, long initialDelay, long period, TimeUnit unit) {
        executor.scheduleAtFixedRate(task, initialDelay, period, unit);
    }

    public DataStore store() {
        return store;
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