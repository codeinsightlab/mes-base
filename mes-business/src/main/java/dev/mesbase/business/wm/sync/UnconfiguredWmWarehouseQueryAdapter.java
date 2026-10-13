package dev.mesbase.business.wm.sync;

import dev.mesbase.core.error.ErrorCode;
import dev.mesbase.core.error.FoundationException;
import dev.mesbase.core.factory.FactoryScope;
import java.util.List;

/**
 * Default fallback. Register exactly one WmWarehouseQueryPort bean when integrating an external source.
 */
public class UnconfiguredWmWarehouseQueryAdapter implements WmWarehouseQueryPort {
    @Override
    public List<ExternalWmWarehouse> query(FactoryScope scope, int maximum) {
        throw new FoundationException(ErrorCode.SYNC_QUERY_NOT_IMPLEMENTED);
    }
}

