package dev.mesbase.business.production;

import dev.mesbase.business.production.domain.ProductionReportModels.ExecutionQuantity;
import dev.mesbase.business.production.domain.ProductionReportModels;
import dev.mesbase.business.production.domain.ProductionReportModels.NodeQuantity;
import dev.mesbase.business.production.domain.ProductionQuantityConflictException;
import dev.mesbase.business.production.service.ProductionQuantityService;
import dev.mesbase.business.production.dao.WorkorderMapper;
import dev.mesbase.business.production.service.ExecutionPathService;
import dev.mesbase.business.production.service.ExecutionPathService.PathModel;
import dev.mesbase.business.production.dao.MesProductionExecutionMapper;
import dev.mesbase.business.production.dao.MesExecutionPathNodeMapper;
import dev.mesbase.business.production.dao.ProductionQuantityMapper;
import dev.mesbase.business.production.domain.MesProductionExecutionEntity;
import dev.mesbase.business.production.domain.MesExecutionPathEntity;
import dev.mesbase.business.production.domain.MesExecutionPathNodeEntity;
import dev.mesbase.business.production.domain.RoutingNodeEntity;
import dev.mesbase.business.directory.service.BusinessDirectoryService;
import dev.mesbase.business.quality.service.ProductionDefectQuantityQuery;
import dev.mesbase.business.quality.dao.MesProductionDefectMapper;
import dev.mesbase.business.quality.domain.ProductionDefectModels.NodeTotal;
import dev.mesbase.business.quality.service.ExecutionQualityReferenceQueryImpl;
import dev.mesbase.business.quality.service.QualityTreatmentService;
import java.math.BigDecimal;
import dev.mesbase.business.production.domain.ProductionInputBalance;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.ArgumentMatchers.any;

class ProductionQualityQuantityTest {
    static BigDecimal d(String value) {
        return new BigDecimal(value);
    }

    static NodeQuantity node(long id, String r, String defects) {
        return new NodeQuantity(1L, id, 1L, "N" + id, "工序", (int) id, BigDecimal.ZERO, d(r), d(defects), BigDecimal.ZERO, BigDecimal.ZERO, true);
    }

    static ExecutionQuantity view(List<NodeQuantity> nodes) {
        return new ExecutionQuantity(1L, 1L, 1L, d("100"), "LOCKED", true, null, nodes, null, d("100"), d("100"), BigDecimal.ZERO);
    }

    @ParameterizedTest
    @CsvSource({"3,1,true", "3,2,true", "3,3,false", "1,-1,false", "2,-3,false", "3,-88,true", "3,-89,false", "1,1,false"})
    void reportCandidateRecomputesWholePathIncludingDefects(long node, String delta, boolean allowed) {
        var q = view(List.of(node(1, "100", "5"), node(2, "95", "3"), node(3, "90", "2")));
        if (allowed) assertDoesNotThrow(() -> ProductionQuantityService.validateChange(q, node, d(delta)));
        else
            assertThrows(ProductionQuantityConflictException.class, () -> ProductionQuantityService.validateChange(q, node, d(delta)));
    }

    @Test
    void zeroDefectsPreserveOriginalCapacityAndExactDecimalArithmetic() {
        var q = view(List.of(node(1, "99.123456", "0"), node(2, "0", "0")));
        assertDoesNotThrow(() -> ProductionQuantityService.validateChange(q, 2L, d("99.123456")));
        assertThrows(ProductionQuantityConflictException.class, () -> ProductionQuantityService.validateChange(q, 2L, d("99.123457")));
    }

    @Test
    void typedQualityQueryUsesSharedTreatmentProjectionAndNeverHidesFailure() {
        var mapper = mock(MesProductionDefectMapper.class);
        var treatments = mock(QualityTreatmentService.class);
        var query = new ExecutionQualityReferenceQueryImpl(mapper, treatments);
        var values = List.of(new NodeTotal(11L, d("5")), new NodeTotal(12L, d("3")));
        when(treatments.nodeTotals(1L)).thenReturn(values);
        assertEquals(values, query.nodeTotals(1L));
        verify(treatments).nodeTotals(1L);
        when(treatments.nodeTotals(2L)).thenThrow(new IllegalStateException("TEST ONLY query failure"));
        assertThrows(IllegalStateException.class, () -> query.nodeTotals(2L));
    }

    @Test
    void readsUseOnlyActualPathAndOneBatchOfEachLedger() {
        var executions = mock(MesProductionExecutionMapper.class);
        var paths = mock(ExecutionPathService.class);
        var selected = mock(MesExecutionPathNodeMapper.class);
        var reports = mock(ProductionQuantityMapper.class);
        var quality = mock(ProductionDefectQuantityQuery.class);
        var execution = new MesProductionExecutionEntity();
        execution.setId(1L);
        execution.setRoutingId(1L);
        execution.setTargetQty(d("100"));
        var path = new MesExecutionPathEntity();
        path.setRoutingId(1L);
        path.setPathStatus("LOCKED");
        var node = new RoutingNodeEntity();
        node.setId(11L);
        node.setOperationId(1L);
        node.setOperationName("首工序");
        var selectedNode = new MesExecutionPathNodeEntity();
        selectedNode.setRoutingNodeId(11L);
        selectedNode.setSequenceNo(1);
        when(executions.selectByPrimaryKey(1L)).thenReturn(execution);
        when(paths.view(1L)).thenReturn(new PathModel(1L, "READY", path, List.of(11L), List.of(node), List.of(), null, Map.of()));
        when(selected.selectByExample(any())).thenReturn(List.of(selectedNode));
        when(reports.nodeTotals(1L)).thenReturn(List.of(new ProductionReportModels.NodeTotal(11L, d("10")), new ProductionReportModels.NodeTotal(99L, d("999"))));
        when(quality.nodeTotals(1L)).thenReturn(List.of(new NodeTotal(11L, d("2")), new NodeTotal(99L, d("900"))));
        when(reports.inputBalance(1L)).thenReturn(new ProductionInputBalance(d("100"), d("0")));
        var service = new ProductionQuantityService(executions, paths, selected, reports, mock(BusinessDirectoryService.class), quality, mock(WorkorderMapper.class));
        var result = service.getExecutionQuantities(1L);
        assertEquals(1, result.nodes().size());
        assertEquals(d("8"), result.nodes().getFirst().effectiveOutputQty());
        verify(quality).nodeTotals(1L);
        verify(reports).nodeTotals(1L);
    }
}
