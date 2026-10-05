package dev.mesbase.infrastructure.persistence;
import dev.mesbase.core.factory.FactoryId;
import dev.mesbase.core.factory.FactoryOwned;
/** DESIGN CONTRACT ONLY. No implementation is registered.
 * Every statement (including id lookup/update/delete, bulk and joins) MUST enforce factory_id.
 * Tenant-local uniqueness and foreign references must include FactoryId.
 */
public interface SharedDatabasePersistence extends BusinessPersistence {
    record ScopedIdentity(FactoryId factoryId,String localId) implements FactoryOwned {
        public ScopedIdentity { java.util.Objects.requireNonNull(factoryId); java.util.Objects.requireNonNull(localId); }
    }
}
