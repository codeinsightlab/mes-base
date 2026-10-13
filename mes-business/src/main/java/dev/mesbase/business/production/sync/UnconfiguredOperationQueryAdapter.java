package dev.mesbase.business.production.sync;

import dev.mesbase.core.factory.FactoryScope;
import dev.mesbase.core.error.ErrorCode;
import dev.mesbase.core.error.FoundationException;
import java.util.List;

public class UnconfiguredOperationQueryAdapter implements OperationQueryPort {
    @Override
    public List<ExternalOperation> query(FactoryScope scope, int maximum) {
        throw new FoundationException(ErrorCode.ERP_OPERATION_NOT_CONFIGURED);
    }
}
