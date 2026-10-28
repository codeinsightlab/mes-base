package dev.mesbase.business.production;

import dev.mesbase.business.integration.warehouse.WarehouseIntegration;
import dev.mesbase.business.production.domain.ProductionScanCode;
import dev.mesbase.business.production.domain.WorkorderEntity;
import dev.mesbase.business.production.service.BusinessSourceRules;
import dev.mesbase.business.production.service.MaterialQuantityRules;
import dev.mesbase.business.production.service.ProductionIdentityService;
import dev.mesbase.business.production.service.ProductionInputSupplyService;
import dev.mesbase.business.wm.service.LocalProductionSupply;
import dev.mesbase.core.error.ErrorCode;
import dev.mesbase.core.error.FoundationException;
import dev.mesbase.core.factory.FactoryId;
import dev.mesbase.core.factory.FactoryScope;
import dev.mesbase.platform.execution.FactoryContext;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.SimpleTransactionStatus;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** LOCAL production-input admission contract; no ERP provider is registered for this use case. */
class ProductionSupplyContractTest {
    @Test
    void externalWorkOrderIsRejectedInsteadOfUsingAnErpSupplyAdapter() {
        var scope = new FactoryScope(new FactoryId("CONTRACT_ONLY"));
        var manager = mock(PlatformTransactionManager.class);
        when(manager.getTransaction(any(TransactionDefinition.class))).thenReturn(new SimpleTransactionStatus());
        var sourceOrder = new WorkorderEntity();
        sourceOrder.setId(1L);
        sourceOrder.setSource("EXTERNAL");
        var identities = mock(ProductionIdentityService.class);
        when(identities.requireInputOrder(new ProductionScanCode("WO", "M", "L"))).thenReturn(sourceOrder);
        var service = new ProductionInputSupplyService(mock(WarehouseIntegration.class),
                mock(LocalProductionSupply.class), manager, mock(BusinessSourceRules.class), identities,
                mock(MaterialQuantityRules.class));

        var failure = assertThrows(FoundationException.class, () -> FactoryContext.withScope(scope,
                () -> service.query("WO:M#L")));

        assertEquals(ErrorCode.BUSINESS_SOURCE_CONFLICT, failure.code());
    }
}
