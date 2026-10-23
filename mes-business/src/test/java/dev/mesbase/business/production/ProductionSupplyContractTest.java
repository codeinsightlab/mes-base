package dev.mesbase.business.production;

import dev.mesbase.business.production.domain.WorkorderEntity;
import dev.mesbase.business.wm.dao.WmWarehouseErpExtraMapper;
import dev.mesbase.framework.sync.SyncCoordinator;
import dev.mesbase.business.production.dao.WorkorderErpExtraMapper;
import dev.mesbase.business.production.service.BusinessSourceRules;
import org.mockito.Mockito;
import dev.mesbase.business.production.service.ProductionInputSupplyService;
import dev.mesbase.business.production.service.ProductionIdentityService;
import dev.mesbase.business.production.domain.ProductionScanCode;
import dev.mesbase.business.integration.production.ProductionSupply.Stock;
import dev.mesbase.business.integration.production.ProductionSupply.Order;
import dev.mesbase.business.integration.production.ProductionSupply.Requirement;
import dev.mesbase.business.integration.production.ProductionSupply.Context;
import dev.mesbase.business.integration.production.ProductionErpAdapter;
import dev.mesbase.business.integration.warehouse.WarehouseIntegration;
import dev.mesbase.business.integration.warehouse.WarehouseIntegration.Selection;
import dev.mesbase.business.integration.warehouse.ErpActionResult.Mode;
import dev.mesbase.business.wm.service.LocalProductionSupply;
import dev.mesbase.core.factory.FactoryScope;
import dev.mesbase.core.factory.FactoryId;
import dev.mesbase.platform.execution.FactoryContext;
import dev.mesbase.core.error.FoundationException;
import dev.mesbase.core.error.ErrorCode;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.transaction.PlatformTransactionManager;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Contract fixture only: CONNECTED does not mean a real ERP implementation or acceptance.
 */
class ProductionSupplyContractTest {
    @Test
    void connectedModeRejectsMultiMaterialAndNeverFallsBackToLocal() {
        var scope = new FactoryScope(new FactoryId("CONTRACT_ONLY"));
        var integration = mock(WarehouseIntegration.class);
        when(integration.submissionSelection(scope)).thenReturn(new Selection(Mode.CONNECTED, "TEST_ONLY", "TEST_BINDING"));
        var port = mock(ProductionErpAdapter.class);
        when(port.supports(scope, "TEST_BINDING")).thenReturn(true);
        var now = LocalDateTime.now();
        var stock = new Stock("S1", 1L, "M", "L", 1L, "W", "B", 1L, "KG", new BigDecimal("999"), now);
        var order = new Order(1L, "WO", "P", "产品", "CENTER", new BigDecimal("100"), "PCS", "ERP_TEST_APPROVED", List.of(1L), List.of(
                new Requirement("M", 1L, 1L, "KG", new BigDecimal("200")), new Requirement("M2", 2L, 1L, "KG", new BigDecimal("50"))), "MO_TEST", now);
        when(port.query(scope, "TEST_BINDING", "WO", "M", "L")).thenReturn(new Context(Mode.CONNECTED.getValue(), List.of(stock), List.of(order)));
        var sourceRules = mock(BusinessSourceRules.class);
        var sourceOrder = new WorkorderEntity();
        sourceOrder.setId(1L);
        sourceOrder.setSource("EXTERNAL");
        when(sourceRules.order(1L)).thenReturn(sourceOrder);
        var identities = mock(ProductionIdentityService.class);
        when(identities.requireInputOrder(new ProductionScanCode("WO", "M", "L"))).thenReturn(sourceOrder);
        var service = new ProductionInputSupplyService(integration, List.of(port), mock(LocalProductionSupply.class), mock(PlatformTransactionManager.class), sourceRules, identities);
        var failure = assertThrows(FoundationException.class, () -> FactoryContext.withScope(scope, () -> service.calculate("WO:M#L", 1L, "S1", BigDecimal.ONE)));
        assertEquals(ErrorCode.PRODUCTION_SINGLE_MATERIAL_REQUIRED, failure.code());
        var absent = new ProductionInputSupplyService(integration, List.of(), mock(LocalProductionSupply.class), mock(PlatformTransactionManager.class), sourceRules, identities);
        assertEquals(ErrorCode.INTEGRATION_NOT_CONFIGURED, assertThrows(FoundationException.class, () -> FactoryContext.withScope(scope, () -> absent.query("WO:M#L"))).code());
    }
}
