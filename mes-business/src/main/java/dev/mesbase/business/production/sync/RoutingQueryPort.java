package dev.mesbase.business.production.sync;

import dev.mesbase.core.factory.FactoryScope;
import java.util.List;

/**
 * Adapter resolves the current factory external source. No default factory or database.
 */
public interface RoutingQueryPort {
    List<ExternalRouting> query(FactoryScope scope, int maximum);
}
