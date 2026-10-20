package dev.mesbase.infrastructure.persistence;

/**
 * Effects outside the database run only after an owned transaction commits.
 */
public interface CommitActions {
    void afterCommit(Runnable action);

    void afterRollback(Runnable action);
}
