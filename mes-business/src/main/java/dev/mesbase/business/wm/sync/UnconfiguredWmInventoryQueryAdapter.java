package dev.mesbase.business.wm.sync;

import dev.mesbase.core.error.ErrorCode;
import dev.mesbase.core.error.FoundationException;
import dev.mesbase.core.factory.FactoryScope;
import java.util.List;

/**
 * Default fallback. Register exactly one WmInventoryQueryPort bean when integrating an external source.
 */
public class UnconfiguredWmInventoryQueryAdapter implements WmInventoryQueryPort {
    @Override
    public List<ExternalWmInventory> query(FactoryScope scope, int maximum) {
        throw new FoundationException(ErrorCode.SYNC_QUERY_NOT_IMPLEMENTED);
    }
}

