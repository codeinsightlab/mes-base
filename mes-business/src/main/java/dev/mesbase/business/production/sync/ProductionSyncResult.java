// MES-CODEGEN: reviewed lifecycle extension of the generated scaffold.
package dev.mesbase.business.production.sync;


public record ProductionSyncResult(
        int fetched, int created, int updated, int skipped
) {
}
