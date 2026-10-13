package dev.mesbase.business.production.sync;


public record ProductionSyncResult(
        int fetched, int created, int updated, int skipped
) {
}
