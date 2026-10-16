package dev.mesbase.business.quality.service;

/**
 * Current business database only; any immutable version, including VOID, is a strong reference.
 * Destructive callers must hold the shared WorkOrder-to-Execution locks in their business transaction.
 */
public interface ExecutionQualityReferenceQuery {
    boolean hasAnyHistory(Long executionId);
}
