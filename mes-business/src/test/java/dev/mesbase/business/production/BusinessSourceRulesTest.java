package dev.mesbase.business.production;

import dev.mesbase.business.production.service.BusinessSourceRules;
import dev.mesbase.business.production.dao.WorkorderMapper;
import dev.mesbase.business.production.dao.WorkorderErpExtraMapper;
import dev.mesbase.business.production.dao.MesProductionReceiptMapper;
import dev.mesbase.business.production.domain.WorkorderEntity;
import dev.mesbase.business.production.domain.MesProductionExecutionEntity;
import dev.mesbase.business.production.domain.WorkorderErpExtraEntity;
import dev.mesbase.business.wm.dao.WmWarehouseMapper;
import dev.mesbase.business.wm.dao.WmWarehouseErpExtraMapper;
import dev.mesbase.business.wm.domain.WmWarehouseEntity;
import dev.mesbase.business.wm.domain.WmWarehouseErpExtraEntity;
import dev.mesbase.business.issue.dao.MesMaterialIssueMapper;
import dev.mesbase.business.issue.dao.MesMaterialIssueItemMapper;
import dev.mesbase.business.integration.warehouse.WarehouseIntegration;
import dev.mesbase.business.integration.warehouse.ErpActionResult;
import dev.mesbase.core.error.FoundationException;
import dev.mesbase.core.error.ErrorCode;
import dev.mesbase.core.factory.FactoryScope;
import dev.mesbase.core.factory.FactoryId;
import dev.mesbase.core.factory.FactoryRuntimeMode;
import dev.mesbase.platform.execution.FactoryIntegrationSnapshot;
import dev.mesbase.platform.execution.FactoryIntegrationContext;
import java.util.List;
import dev.mesbase.business.integration.production.ProductionSupply.Context;
import dev.mesbase.business.integration.production.ProductionSupply.Order;
import dev.mesbase.business.integration.production.ProductionSupply.Stock;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertFalse;

class BusinessSourceRulesTest {
    private final FactoryId id = new FactoryId("TEST");
    private final WorkorderMapper orders = mock(WorkorderMapper.class);
    private final WorkorderErpExtraMapper orderExtras = mock(WorkorderErpExtraMapper.class);
    private final WmWarehouseMapper warehouses = mock(WmWarehouseMapper.class);
    private final WmWarehouseErpExtraMapper warehouseExtras = mock(WmWarehouseErpExtraMapper.class);
    private final WarehouseIntegration integration = new WarehouseIntegration(List.of());
    private final BusinessSourceRules rules = new BusinessSourceRules(integration, orders, orderExtras, warehouses, warehouseExtras,
            mock(MesMaterialIssueMapper.class), mock(MesMaterialIssueItemMapper.class), mock(MesProductionReceiptMapper.class));

    private void mode(boolean local, Runnable task) {
        FactoryIntegrationContext.run(new FactoryIntegrationSnapshot(id, local ? FactoryRuntimeMode.LOCAL : FactoryRuntimeMode.ERP,
                local ? null : "TEST-ERP", local ? null : "TEST-BINDING", 1, local ? null : 999L), task);
    }

    private void mappings() {
        var e = new WorkorderErpExtraEntity();
        e.setId(1L);
        e.setExternalId("ERP-ORDER");
        e.setProvider("TEST-ERP");
        e.setBindingKey("TEST-BINDING");
        e.setConfigurationRevision(Long.valueOf("999"));
        when(orderExtras.selectByPrimaryKey(1L)).thenReturn(e);
        var mappings = List.of(10L, 11L).stream().map(n -> {
            var x = new WmWarehouseErpExtraEntity();
            x.setId(n);
            x.setExternalId("ERP-W" + n);
            x.setProvider("TEST-ERP");
            x.setBindingKey("TEST-BINDING");
            x.setConfigurationRevision(Long.valueOf("999"));
            return x;
        }).toList();
        when(warehouseExtras.selectByExample(any())).thenReturn(mappings);
    }

    @Test
    void completedParentAllowsOnlyFinishedOutputAndStillChecksPersistedSources() {
        var order = new WorkorderEntity();
        order.setId(1L);
        order.setSource("LOCAL");
        order.setStatus("COMPLETED");
        when(orders.selectByPrimaryKey(1L)).thenReturn(order);
        var execution = new MesProductionExecutionEntity();
        execution.setId(1L);
        execution.setWorkorderId(1L);
        mode(true, () -> {
            assertDoesNotThrow(() -> rules.finishedOutput(execution, null));
            assertThrows(FoundationException.class, () -> rules.execution(execution, null));
            for (String status : List.of("CANCELLED", "HISTORICAL")) {
                order.setStatus(status);
                assertThrows(FoundationException.class, () -> rules.finishedOutput(execution, null));
            }
            order.setStatus("COMPLETED");
            order.setSource("EXTERNAL");
            assertThrows(FoundationException.class, () -> rules.finishedOutput(execution, null));
        });
    }

    @Test
    void allTriplesAndBothModesAdmitOnlyMatchingAuthority() {
        mappings();
        for (String orderSource : List.of("LOCAL", "EXTERNAL"))
            for (String raw : List.of("LOCAL", "EXTERNAL"))
                for (String finished : List.of("LOCAL", "EXTERNAL"))
                    for (boolean local : List.of(true, false)) {
                        var o = new WorkorderEntity();
                        o.setId(1L);
                        o.setStatus("RELEASED");
                        o.setSource(orderSource);
                        when(orders.selectByPrimaryKey(1L)).thenReturn(o);
                        var w = new WmWarehouseEntity();
                        w.setId(10L);
                        w.setSource(raw);
                        var f = new WmWarehouseEntity();
                        f.setId(11L);
                        f.setSource(finished);
                        when(warehouses.selectByExample(any())).thenReturn(List.of(w, f));
                        mode(local, () -> {
                            Runnable operation = () -> {
                                rules.order(1L);
                                rules.warehouses(orderSource, List.of(10L, 11L));
                            };
                            if (orderSource.equals(raw) && raw.equals(finished) && local == orderSource.equals("LOCAL"))
                                assertDoesNotThrow(operation::run);
                            else
                                assertEquals(ErrorCode.BUSINESS_SOURCE_CONFLICT, assertThrows(FoundationException.class, operation::run).code());
                        });
                    }
    }

    @Test
    void unknownSourceAndMissingOrChangedExternalMappingFailClosed() {
        mode(true, () -> {
            assertThrows(FoundationException.class, () -> rules.current(null));
            assertThrows(FoundationException.class, () -> rules.current("INVALID"));
        });
        var o = new WorkorderEntity();
        o.setId(1L);
        o.setStatus("RELEASED");
        o.setSource("EXTERNAL");
        when(orders.selectByPrimaryKey(1L)).thenReturn(o);
        mode(false, () -> assertThrows(FoundationException.class, () -> rules.order(1L)));
        mappings();
        var extra = orderExtras.selectByPrimaryKey(1L);
        extra.setBindingKey("OTHER");
        mode(false, () -> assertThrows(FoundationException.class, () -> rules.order(1L)));
    }

    @Test
    void externalSourcePassesAdmissionButCannotManufactureAnErpAdapter() {
        mappings();
        var o = new WorkorderEntity();
        o.setId(1L);
        o.setStatus("RELEASED");
        o.setSource("EXTERNAL");
        when(orders.selectByPrimaryKey(1L)).thenReturn(o);
        mode(false, () -> {
            assertDoesNotThrow(() -> rules.order(1L));
            assertEquals(ErrorCode.INTEGRATION_NOT_CONFIGURED,
                    assertThrows(FoundationException.class, () -> integration.submissionSelection(new FactoryScope(id))).code());
        });
        assertFalse(new ErpActionResult(ErpActionResult.Status.SUCCEEDED, ErpActionResult.Mode.CONNECTED, "CREATED", "单据仅创建").stockEffective());
    }

    @Test
    void frozenLocalRecoveryIgnoresCurrentModeButNeverUsesAnExternalWarehouse() {
        var order = new WorkorderEntity();
        order.setId(1L);
        order.setSource("LOCAL");
        var warehouse = new WmWarehouseEntity();
        warehouse.setId(10L);
        warehouse.setSource("LOCAL");
        when(orders.selectByPrimaryKey(1L)).thenReturn(order);
        when(warehouses.selectByPrimaryKey(10L)).thenReturn(warehouse);
        mode(false, () -> assertDoesNotThrow(() -> rules.frozenLocal(1L, 10L)));
        order.setSource(null);
        warehouse.setSource(null);
        mode(false, () -> assertDoesNotThrow(() -> rules.frozenLocal(1L, 10L)));
        warehouse.setSource("EXTERNAL");
        mode(false, () -> assertThrows(FoundationException.class, () -> rules.frozenLocal(1L, 10L)));
    }

    @Test
    void erpPreviewCannotPublishLocalOrUnmappedCandidates() {
        mappings();
        var sourceOrder = new WorkorderEntity();
        sourceOrder.setId(1L);
        sourceOrder.setSource("LOCAL");
        when(orders.selectByExample(any())).thenReturn(List.of(sourceOrder));
        var verifiedMapping = orderExtras.selectByPrimaryKey(1L);
        when(orderExtras.selectByExample(any())).thenReturn(List.of(verifiedMapping));
        var order = mock(Order.class);
        when(order.workorderId()).thenReturn(1L);
        var a = mock(Stock.class);
        when(a.warehouseId()).thenReturn(10L);
        var b = mock(Stock.class);
        when(b.warehouseId()).thenReturn(11L);
        var wa = new WmWarehouseEntity();
        wa.setId(10L);
        wa.setSource("EXTERNAL");
        var wb = new WmWarehouseEntity();
        wb.setId(11L);
        wb.setSource("EXTERNAL");
        when(warehouses.selectByExample(any())).thenReturn(List.of(wa, wb));
        var context = new Context("CONNECTED", List.of(a, b), List.of(order));
        mode(false, () -> assertThrows(FoundationException.class, () -> rules.externalCandidates(context)));
        sourceOrder.setSource("EXTERNAL");
        mode(false, () -> assertDoesNotThrow(() -> rules.externalCandidates(context)));
        when(orderExtras.selectByExample(any())).thenReturn(List.of());
        mode(false, () -> assertThrows(FoundationException.class, () -> rules.externalCandidates(context)));
    }

}
