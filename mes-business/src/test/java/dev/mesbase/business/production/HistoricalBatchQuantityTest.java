package dev.mesbase.business.production;

import dev.mesbase.business.directory.service.BusinessDirectoryService;
import dev.mesbase.business.legacy.dao.LegacyMigrationMapper;
import dev.mesbase.business.legacy.domain.LegacyHistory;
import dev.mesbase.business.production.dao.MesExecutionPathNodeMapper;
import dev.mesbase.business.production.dao.MesProductionExecutionMapper;
import dev.mesbase.business.production.dao.ProductionQuantityMapper;
import dev.mesbase.business.production.dao.WorkorderMapper;
import dev.mesbase.business.production.domain.MesExecutionPathEntity;
import dev.mesbase.business.production.domain.MesExecutionPathNodeEntity;
import dev.mesbase.business.production.domain.MesProductionExecutionEntity;
import dev.mesbase.business.production.domain.ProductionInputBalance;
import dev.mesbase.business.production.domain.ProductionReportModels.ExecutionInputBalance;
import dev.mesbase.business.production.domain.ProductionReportModels.ExecutionNodeTotal;
import dev.mesbase.business.production.domain.ProductionReportModels.NodeTotal;
import dev.mesbase.business.production.domain.RoutingNodeEntity;
import dev.mesbase.business.production.domain.WorkorderEntity;
import dev.mesbase.business.production.service.ExecutionPathService;
import dev.mesbase.business.production.service.ExecutionPathService.PathBatch;
import dev.mesbase.business.production.service.ExecutionPathService.PathModel;
import dev.mesbase.business.production.service.ProductionQuantityService;
import dev.mesbase.business.quality.service.ProductionDefectQuantityQuery;
import dev.mesbase.core.error.FoundationException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

/**
 * Shared node IDs across executions must never merge quantities; batch and single use the same authority.
 */
class HistoricalBatchQuantityTest {
    private final MesProductionExecutionMapper executions = mock(MesProductionExecutionMapper.class);
    private final MesExecutionPathNodeMapper selected = mock(MesExecutionPathNodeMapper.class);
    private final ExecutionPathService paths = mock(ExecutionPathService.class);
    private final ProductionQuantityMapper quantities = mock(ProductionQuantityMapper.class);
    private final ProductionDefectQuantityQuery defects = mock(ProductionDefectQuantityQuery.class);
    private final WorkorderMapper orders = mock(WorkorderMapper.class);
    private final LegacyMigrationMapper history = mock(LegacyMigrationMapper.class);
    private final Map<Long, MesProductionExecutionEntity> rows = new LinkedHashMap<>();
    private final Map<Long, PathModel> models = new LinkedHashMap<>();
    private final Map<Long, List<MesExecutionPathNodeEntity>> choices = new LinkedHashMap<>();
    private final ProductionQuantityService service;

    @SuppressWarnings("unchecked")
    HistoricalBatchQuantityTest() {
        ObjectProvider<LegacyMigrationMapper> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(history);
        service = new ProductionQuantityService(executions, paths, selected, quantities,
                mock(BusinessDirectoryService.class), defects, orders, provider);
        for (long id : List.of(1L, 2L)) {
            var execution = new MesProductionExecutionEntity();
            execution.setId(id);
            execution.setWorkorderId(9L);
            execution.setRoutingId(7L);
            execution.setExecutionNo("TEST-" + id);
            execution.setStatus("HISTORICAL");
            execution.setTargetQty(q(id * 100));
            var path = new MesExecutionPathEntity();
            path.setRoutingId(7L);
            path.setPathStatus("LOCKED");
            var node = new RoutingNodeEntity();
            node.setId(11L);
            node.setRoutingId(7L);
            node.setNodeCode("N");
            node.setOperationId(5L);
            node.setOperationName("TEST ONLY");
            node.setDisplayOrder(1);
            var choice = new MesExecutionPathNodeEntity();
            choice.setExecutionId(id);
            choice.setRoutingNodeId(11L);
            choice.setSequenceNo(1);
            rows.put(id, execution);
            choices.put(id, List.of(choice));
            models.put(id, new PathModel(id, "HISTORICAL", path, List.of(11L), List.of(node), List.of(), execution.getExecutionNo(), null, Map.of()));
            when(executions.selectByPrimaryKey(id)).thenReturn(execution);
            when(paths.view(id)).thenReturn(models.get(id));
            when(quantities.inputBalance(id)).thenReturn(new ProductionInputBalance(BigDecimal.ZERO, BigDecimal.ZERO));
            when(history.input(id)).thenReturn(q(id * 100));
            when(history.nodes(id)).thenReturn(List.of(new LegacyHistory.Node(11L, q(id * 100 - 4), q(4), q(4), "{}")));
            when(quantities.nodeTotals(id)).thenReturn(List.of(new NodeTotal(11L, q(id * 100 - 4))));
            when(defects.nodeTotals(id)).thenReturn(List.of());
        }
        var order = new WorkorderEntity();
        order.setId(9L);
        order.setStatus("HISTORICAL");
        when(orders.selectByPrimaryKey(9L)).thenReturn(order);
        when(orders.selectByExample(any())).thenReturn(List.of(order));
        when(paths.viewBatch(any())).thenReturn(new PathBatch(rows, models, choices));
        when(history.inputs(any())).thenReturn(List.of(new LegacyHistory.Input(1L, q(100)), new LegacyHistory.Input(2L, q(200))));
        when(history.nodesBatch(any())).thenReturn(List.of(new LegacyHistory.ExecutionNode(1L, 11L, q(96), q(4), q(4)), new LegacyHistory.ExecutionNode(2L, 11L, q(196), q(4), q(4))));
        when(quantities.inputBalances(any())).thenReturn(List.of(new ExecutionInputBalance(1L, BigDecimal.ZERO, BigDecimal.ZERO), new ExecutionInputBalance(2L, BigDecimal.ZERO, BigDecimal.ZERO)));
        when(quantities.nodeTotalsBatch(any())).thenReturn(List.of(new ExecutionNodeTotal(1L, 11L, q(96)), new ExecutionNodeTotal(2L, 11L, q(196))));
        when(defects.nodeTotalsBatch(any())).thenReturn(Map.of());
    }

    private static BigDecimal q(long n) {
        return BigDecimal.valueOf(n);
    }

    @Test
    void batchEqualsSingleAndDoesNotQueryInExecutionLoop() {
        var batch = service.getHistoricalExecutionQuantities(List.of(1L, 2L));
        verify(paths).viewBatch(List.of(1L, 2L));
        verify(history).inputs(any());
        verify(history).nodesBatch(any());
        verify(quantities).inputBalances(any());
        verify(quantities).nodeTotalsBatch(any());
        verify(defects).nodeTotalsBatch(any());
        verify(orders).selectByExample(any());
        verifyNoMoreInteractions(paths, history, quantities, defects, orders);
        for (long id : List.of(1L, 2L)) {
            when(selected.selectByExample(any())).thenReturn(choices.get(id));
            assertEquals(service.getExecutionQuantities(id), batch.get(id));
            assertFalse(batch.get(id).writable());
            assertEquals(q(id * 100 - 4), batch.get(id).nodes().getFirst().effectiveOutputQty());
        }
    }

    @Test
    void missingEvidenceOrMixedLiveExecutionFailsClosed() {
        when(history.nodesBatch(any())).thenReturn(List.of());
        assertThrows(FoundationException.class, () -> service.getHistoricalExecutionQuantities(List.of(1L, 2L)));
        rows.get(1L).setStatus("READY");
        assertThrows(FoundationException.class, () -> service.getHistoricalExecutionQuantities(List.of(1L, 2L)));
    }

    @Test
    void wrongPathSequenceAndConflictingLedgerStillFail() {
        choices.get(2L).getFirst().setSequenceNo(2);
        assertThrows(FoundationException.class, () -> service.getHistoricalExecutionQuantities(List.of(1L, 2L)));
        choices.get(2L).getFirst().setSequenceNo(1);
        when(quantities.nodeTotalsBatch(any())).thenReturn(List.of(new ExecutionNodeTotal(1L, 11L, q(99))));
        assertThrows(FoundationException.class, () -> service.getHistoricalExecutionQuantities(List.of(1L, 2L)));
    }
}
