package dev.mesbase.platform.execution;

import java.util.concurrent.*;
import java.util.Objects;

/**
 * Scope is explicit; no actor/security context propagation and no commonPool.
 */
public final class ControlledExecutor implements AutoCloseable {
    private final ThreadPoolExecutor pool;

    public ControlledExecutor(int threads, int queueCapacity) {
        if (threads < 1 || queueCapacity < 1) throw new IllegalArgumentException("Invalid executor bounds");
        pool = new ThreadPoolExecutor(threads, threads, 0, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(queueCapacity),
                Thread.ofPlatform().name("mes-execution-", 0).factory(), (task, executor) -> {
            if (executor.isShutdown()) throw new RejectedExecutionException("Executor closed");
            task.run();
        });
    }

    public Future<?> submit(ExecutionScope scope, Runnable task) {
        Objects.requireNonNull(scope);
        Objects.requireNonNull(task);
        FutureTask<Void> future = new FutureTask<>(() -> {
            scope.run(task);
            return null;
        });
        pool.execute(future);
        return future;
    }

    @Override
    public void close() {
        pool.close();
    }
}
