package dev.mesbase.acceptance;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executor;

/**
 * TEST ONLY deterministic queue; never inline inside ConcurrentHashMap.compute.
 */
public class QueuedCompletionExecutor implements Executor {
    private final ConcurrentLinkedQueue<Runnable> queue = new ConcurrentLinkedQueue<>();

    public void execute(Runnable task) {
        queue.add(task);
    }

    public int size() {
        return queue.size();
    }

    public void runAll() {
        for (int i = 0; i < 1000; i++) {
            var task = queue.poll();
            if (task == null) return;
            task.run();
        }
        throw new AssertionError("Completion queue did not drain");
    }
}
