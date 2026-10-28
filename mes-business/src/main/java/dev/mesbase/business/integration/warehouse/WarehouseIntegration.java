package dev.mesbase.business.integration.warehouse;

import dev.mesbase.core.factory.FactoryScope;
import dev.mesbase.core.factory.FactoryRuntimeMode;
import dev.mesbase.platform.execution.FactoryContext;
import dev.mesbase.platform.execution.FactoryIntegrationContext;
import dev.mesbase.platform.execution.FactoryIntegrationSnapshot;
import dev.mesbase.core.error.ErrorCode;
import dev.mesbase.core.error.FoundationException;
import org.springframework.stereotype.Component;
import dev.mesbase.business.integration.warehouse.ErpActionResult.Mode;

@Component
public class WarehouseIntegration {
    private FactoryIntegrationSnapshot snapshot(FactoryScope scope) {
        if (FactoryContext.current().isPresent() && !FactoryContext.required().equals(scope))
            throw new FoundationException(ErrorCode.CONFLICT);
        var snapshot = FactoryIntegrationContext.required();
        if (!snapshot.factoryId().equals(scope.factoryId()))
            throw new FoundationException(ErrorCode.CONFLICT);
        return snapshot;
    }

    public Mode configuredMode(FactoryScope scope) {
        return snapshot(scope).mode() == FactoryRuntimeMode.LOCAL ? Mode.LOCAL_ONLY : Mode.CONNECTED;
    }

}
