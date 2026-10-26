package dev.mesbase.business.production;

import dev.mesbase.business.basic.dao.MaterialMapper;
import dev.mesbase.business.directory.service.BusinessDirectoryService;
import dev.mesbase.business.issue.dao.MesMaterialIssueMapper;
import dev.mesbase.business.production.dao.MesExecutionAssignmentMapper;
import dev.mesbase.business.production.dao.MesExecutionPathMapper;
import dev.mesbase.business.production.dao.MesExecutionPathNodeMapper;
import dev.mesbase.business.production.dao.MesProductionExecutionMapper;
import dev.mesbase.business.production.dao.MesProductionReportMapper;
import dev.mesbase.business.production.dao.MesWorkorderMaterialMapper;
import dev.mesbase.business.production.dao.ProductionBatchMapper;
import dev.mesbase.business.production.dao.ProductionQuantityMapper;
import dev.mesbase.business.production.dao.RoutingEdgeMapper;
import dev.mesbase.business.production.dao.RoutingMapper;
import dev.mesbase.business.production.dao.RoutingNodeMapper;
import dev.mesbase.business.production.dao.RoutingProductMapper;
import dev.mesbase.business.production.dao.WorkOrderExtraMapper;
import dev.mesbase.business.production.dao.WorkorderErpExtraMapper;
import dev.mesbase.business.production.dao.WorkorderMapper;
import dev.mesbase.business.production.domain.MesProductionExecutionEntity;
import dev.mesbase.business.production.domain.WorkOrderModels;
import dev.mesbase.business.production.domain.WorkorderEntity;
import dev.mesbase.business.production.service.BusinessSourceRules;
import dev.mesbase.business.production.service.ProductionChainLocks;
import dev.mesbase.business.production.service.ProductionExecutionRules;
import dev.mesbase.business.production.service.MaterialQuantityRules;
import dev.mesbase.business.production.service.RoutingReferenceRules;
import dev.mesbase.business.production.service.WorkOrderService;
import dev.mesbase.business.production.service.WorkorderErpFacts;
import dev.mesbase.business.quality.service.ExecutionQualityReferenceQuery;
import dev.mesbase.common.utils.SecurityUtils;
import dev.mesbase.core.error.ErrorCode;
import dev.mesbase.core.error.FoundationException;
import dev.mesbase.framework.sync.SyncCoordinator;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LocalMaintenanceRulesTest {
    private final WorkorderMapper orders = mock(WorkorderMapper.class);
    private final MesProductionExecutionMapper executions = mock(MesProductionExecutionMapper.class);
    private final WorkOrderExtraMapper queries = mock(WorkOrderExtraMapper.class);
    private final MesMaterialIssueMapper issues = mock(MesMaterialIssueMapper.class);
    private final MesProductionReportMapper reports = mock(MesProductionReportMapper.class);
    private final MesExecutionAssignmentMapper assignments = mock(MesExecutionAssignmentMapper.class);
    private final ProductionChainLocks locks = mock(ProductionChainLocks.class);
    private final ExecutionQualityReferenceQuery quality = mock(ExecutionQualityReferenceQuery.class);
    private final MesExecutionPathMapper paths = mock(MesExecutionPathMapper.class);
    private final BusinessSourceRules sources = mock(BusinessSourceRules.class);
    private final WorkorderEntity order = new WorkorderEntity();

    private WorkOrderService workorders(String status) {
        order.setId(1L);
        order.setSource("LOCAL");
        order.setStatus(status);
        order.setRevision(0);
        order.setProductId(2L);
        order.setRoutingId(3L);
        order.setPlannedQty(new BigDecimal("10"));
        when(orders.selectByPrimaryKeyForUpdate(1L)).thenReturn(order);
        when(orders.selectByPrimaryKey(1L)).thenReturn(order);
        when(orders.updateByPrimaryKey(order)).thenReturn(1);
        when(orders.deleteByPrimaryKey(1L)).thenReturn(1);
        when(queries.activeAllocation(1L, null)).thenReturn(new WorkOrderModels.Allocation(
                1L, new BigDecimal("10"), new BigDecimal("6"), new BigDecimal("4"), BigDecimal.ZERO, 1L));
        var service = spy(new WorkOrderService(orders, mock(ProductionBatchMapper.class), mock(RoutingMapper.class),
                mock(RoutingProductMapper.class), mock(RoutingNodeMapper.class), mock(RoutingEdgeMapper.class),
                mock(MaterialMapper.class), mock(RoutingReferenceRules.class), issues, queries, executions,
                mock(WorkorderErpFacts.class), mock(BusinessDirectoryService.class), mock(MesWorkorderMaterialMapper.class),
                sources, mock(WorkorderErpExtraMapper.class), mock(SyncCoordinator.class), mock(MaterialQuantityRules.class)));
        doReturn(null).when(service).detail(1L);
        return service;
    }

    private WorkOrderModels.Save input(String quantity) {
        return new WorkOrderModels.Save("LOCAL-WO", 2L, new BigDecimal(quantity), 3L,
                null, null, null, null, "edited remark", 0);
    }

    @Test
    void draftPlanCanChangeWithinAllocatedFloor() {
        String status = "DRAFT";
        workorders(status).update(1L, input("6"));
        assertEquals(0, new BigDecimal("6").compareTo(order.getPlannedQty()));
        assertEquals(status, order.getStatus());
        assertEquals(1, order.getRevision());
        verify(orders).updateByPrimaryKey(order);
    }

    @Test
    void planCannotShrinkBelowExecutionTargets() {
        var service = workorders("DRAFT");
        assertEquals(ErrorCode.WORKORDER_QUANTITY_CONFLICT,
                assertThrows(FoundationException.class, () -> service.update(1L, input("5"))).code());
        verify(orders, never()).updateByPrimaryKey(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"RELEASED", "COMPLETED", "CANCELLED"})
    void onlyDraftOrdersAllowEditing(String status) {
        var service = workorders(status);
        assertEquals(ErrorCode.WORKORDER_INVALID,
                assertThrows(FoundationException.class, () -> service.update(1L, input("11"))).code());
        assertEquals(ErrorCode.WORKORDER_INVALID,
                assertThrows(FoundationException.class, () -> service.update(1L, input("10.000000"))).code());
        verify(orders, never()).updateByPrimaryKey(any());
        verify(queries, never()).activeAllocation(any(), any());
    }

    @Test
    void unchangedQuantityDoesNotBlockMetadataOnAnOverallocatedOrder() {
        var service = workorders("DRAFT");
        when(queries.activeAllocation(1L, null)).thenReturn(new WorkOrderModels.Allocation(
                1L, BigDecimal.TEN, new BigDecimal("12"), new BigDecimal("-2"), new BigDecimal("2"), 1L));
        service.update(1L, input("10"));
        assertEquals("edited remark", order.getRemark());
    }

    @Test
    void changingRoutingIsBlockedWhenExecutionsExist() {
        var service = workorders("DRAFT");
        when(executions.countByExample(any())).thenReturn(1L);
        var changed = new WorkOrderModels.Save("LOCAL-WO", 2L, BigDecimal.TEN, 4L,
                null, null, null, null, null, 0);
        assertEquals(ErrorCode.EXECUTION_FROZEN,
                assertThrows(FoundationException.class, () -> service.update(1L, changed)).code());
    }

    @Test
    void externalOrdersHaveNoLocalUpdateOrDeleteFallback() {
        var service = workorders("RELEASED");
        order.setSource("EXTERNAL");
        assertEquals(ErrorCode.EXTERNAL_SOURCE_READ_ONLY,
                assertThrows(FoundationException.class, () -> service.update(1L, input("10"))).code());
        assertEquals(ErrorCode.EXTERNAL_SOURCE_READ_ONLY,
                assertThrows(FoundationException.class, () -> service.delete(1L, 0)).code());
        verify(orders, never()).deleteByPrimaryKey(any());
    }

    @Test
    void staleRevisionCannotOverwritePlan() {
        var service = workorders("DRAFT");
        order.setRevision(1);
        assertEquals(ErrorCode.RESOURCE_CONFLICT,
                assertThrows(FoundationException.class, () -> service.update(1L, input("11"))).code());
    }

    @Test
    void releasedOrderWithoutDownstreamCanBeDeleted() {
        workorders("RELEASED").delete(1L, 0);
        verify(orders).deleteByPrimaryKey(1L);
    }

    @Test
    void cancellationOrWarehouseReferenceBlocksOrderDeletion() {
        var service = workorders("RELEASED");
        when(queries.hasOrderDependents(1L)).thenReturn(true);
        assertEquals(ErrorCode.WORKORDER_REFERENCED,
                assertThrows(FoundationException.class, () -> service.delete(1L, 0)).code());
        verify(orders, never()).deleteByPrimaryKey(any());
    }

    @Test
    void releasedOrderCanReturnToDraftWithoutDownstream() {
        workorders("RELEASED").recall(1L, 0);
        assertEquals("DRAFT", order.getStatus());
        assertEquals(1, order.getRevision());
        verify(orders).updateByPrimaryKey(order);
    }

    @Test
    void recallAndDeleteBothRejectExecutionReferences() {
        var service = workorders("RELEASED");
        when(executions.countByExample(any())).thenReturn(1L);
        assertEquals(ErrorCode.EXECUTION_REFERENCED,
                assertThrows(FoundationException.class, () -> service.recall(1L, 0)).code());
        assertEquals(ErrorCode.EXECUTION_REFERENCED,
                assertThrows(FoundationException.class, () -> service.delete(1L, 0)).code());
        assertEquals("RELEASED", order.getStatus());
        verify(orders, never()).updateByPrimaryKey(any());
    }

    @Test
    void recallRejectsWarehouseReferencesAndExternalSource() {
        var service = workorders("RELEASED");
        when(queries.hasOrderDependents(1L)).thenReturn(true);
        assertEquals(ErrorCode.WORKORDER_REFERENCED,
                assertThrows(FoundationException.class, () -> service.recall(1L, 0)).code());
        order.setSource("EXTERNAL");
        assertEquals(ErrorCode.EXTERNAL_SOURCE_READ_ONLY,
                assertThrows(FoundationException.class, () -> service.recall(1L, 0)).code());
    }

    @Test
    void terminalStatusAndStaleRevisionCannotRecall() {
        var service = workorders("COMPLETED");
        assertEquals(ErrorCode.WORKORDER_INVALID,
                assertThrows(FoundationException.class, () -> service.recall(1L, 0)).code());
        order.setStatus("RELEASED");
        assertEquals(ErrorCode.RESOURCE_CONFLICT,
                assertThrows(FoundationException.class, () -> service.recall(1L, 1)).code());
    }

    private ProductionExecutionRules executionRules() {
        workorders("RELEASED");
        return new ProductionExecutionRules(executions, orders, mock(MaterialMapper.class), mock(RoutingMapper.class),
                mock(RoutingProductMapper.class), issues, locks, paths, mock(MesExecutionPathNodeMapper.class), reports,
                mock(ProductionQuantityMapper.class), quality, assignments, sources, queries,
                mock(MaterialQuantityRules.class));
    }

    private MesProductionExecutionEntity execution() {
        var row = new MesProductionExecutionEntity();
        row.setId(5L);
        row.setWorkorderId(1L);
        row.setProductId(2L);
        row.setRoutingId(3L);
        row.setTargetQty(BigDecimal.TEN);
        row.setStatus("RUNNING");
        row.setStartTime(LocalDateTime.of(2026, 1, 1, 10, 0));
        when(locks.execution(5L)).thenReturn(row);
        return row;
    }

    @Test
    void startedStateAloneDoesNotBlockExecutionDeletion() {
        var rules = executionRules();
        execution();
        rules.deleting(5L);
        verify(queries).hasExecutionDependents(5L);
        verify(quality).hasAnyHistory(5L);
    }

    @Test
    void receiptOrOtherDownstreamBlocksExecutionDeletion() {
        var rules = executionRules();
        execution();
        when(queries.hasExecutionDependents(5L)).thenReturn(true);
        assertEquals(ErrorCode.EXECUTION_REFERENCED,
                assertThrows(FoundationException.class, () -> rules.deleting(5L)).code());
    }

    @Test
    void reportBlocksExecutionDeletion() {
        var rules = executionRules();
        execution();
        when(reports.countByExample(any())).thenReturn(1L);
        assertEquals(ErrorCode.EXECUTION_REFERENCED,
                assertThrows(FoundationException.class, () -> rules.deleting(5L)).code());
    }

    @Test
    void qualityHistoryBlocksExecutionDeletion() {
        var rules = executionRules();
        execution();
        when(quality.hasAnyHistory(5L)).thenReturn(true);
        assertEquals(ErrorCode.EXECUTION_QUALITY_REFERENCED,
                assertThrows(FoundationException.class, () -> rules.deleting(5L)).code());
    }

    @Test
    void completedExecutionAllowsRemarkEditWithoutChangingFacts() {
        var rules = executionRules();
        var before = execution();
        before.setStatus("COMPLETED");
        before.setEndTime(before.getStartTime().plusHours(1));
        var changed = execution();
        changed.setStatus(before.getStatus());
        changed.setEndTime(before.getEndTime());
        changed.setRemark("corrected remark");
        try (var security = mockStatic(SecurityUtils.class)) {
            security.when(SecurityUtils::getUserId).thenReturn(100L);
            rules.updating(before, changed);
        }
        assertEquals("corrected remark", changed.getRemark());
        verify(queries, never()).activeAllocation(any(), any());
    }
}
