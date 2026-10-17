package dev.mesbase.platform.execution;

import dev.mesbase.core.factory.FactoryScope;

/** Resolve on the worker thread and run with a bounded, explicitly established factory configuration. */
@FunctionalInterface
public interface FactoryIntegrationExecutor {
    void run(FactoryScope scope, Runnable task);
}
