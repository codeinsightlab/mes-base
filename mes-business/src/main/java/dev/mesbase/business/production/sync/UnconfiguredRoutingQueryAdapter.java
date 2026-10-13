package dev.mesbase.business.production.sync;

import dev.mesbase.core.factory.FactoryScope;
import dev.mesbase.core.error.ErrorCode;
import dev.mesbase.core.error.FoundationException;
import java.util.List;

public class UnconfiguredRoutingQueryAdapter implements RoutingQueryPort {
    @Override
    public List<ExternalRouting> query(FactoryScope scope, int maximum) {
        throw new FoundationException(ErrorCode.ERP_ROUTING_NOT_CONFIGURED);
    }
}
