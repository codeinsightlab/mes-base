package dev.mesbase.business.production;

import dev.mesbase.framework.sync.SyncCoordinator;
import dev.mesbase.business.production.dao.WorkorderErpExtraMapper;
import dev.mesbase.business.production.service.BusinessSourceRules;
import org.mockito.Mockito;
import dev.mesbase.business.production.service.WorkorderErpFacts;
import dev.mesbase.business.directory.service.BusinessDirectoryService;

import dev.mesbase.business.basic.dao.MaterialMapper;
import dev.mesbase.business.issue.dao.MesMaterialIssueMapper;
import dev.mesbase.business.production.dao.MesProductionExecutionMapper;
import dev.mesbase.business.production.dao.ProductionBatchMapper;
import dev.mesbase.business.production.dao.RoutingEdgeMapper;
import dev.mesbase.business.production.dao.RoutingMapper;
import dev.mesbase.business.production.dao.RoutingNodeMapper;
import dev.mesbase.business.production.dao.RoutingProductMapper;
import dev.mesbase.business.production.dao.WorkOrderExtraMapper;
import dev.mesbase.business.production.dao.WorkorderMapper;
import dev.mesbase.business.production.domain.MesProductionExecutionExample;
import dev.mesbase.business.production.domain.WorkorderEntity;
import dev.mesbase.business.production.domain.WorkOrderModels;
import dev.mesbase.business.production.service.RoutingReferenceRules;
import dev.mesbase.business.production.service.WorkOrderService;
import dev.mesbase.business.production.service.MaterialQuantityRules;
import dev.mesbase.system.service.AutoCodeNumbering;
import dev.mesbase.business.production.dao.MesWorkorderMaterialMapper;
import dev.mesbase.core.error.ErrorCode;
import dev.mesbase.core.error.FoundationException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Unit evidence for reverse-reference guards at the existing WorkOrder entry.
 */
class WorkOrderExecutionProtectionTest {
    @Test
    void referencedDraftCannotBeDeletedOrChangeProduct() {
        var orders = mock(WorkorderMapper.class);
        var executions = mock(MesProductionExecutionMapper.class);
        var row = new WorkorderEntity();
        row.setId(1L);
        row.setRevision(0);
        row.setStatus("DRAFT");
        row.setSource("LOCAL");
        row.setProductId(1L);
        when(orders.selectByPrimaryKey(1L)).thenReturn(row);
        when(orders.selectByPrimaryKeyForUpdate(1L)).thenReturn(row);
        when(executions.countByExample(any(MesProductionExecutionExample.class))).thenReturn(1L);
        var service = new WorkOrderService(orders, mock(ProductionBatchMapper.class), mock(RoutingMapper.class), mock(RoutingProductMapper.class), mock(RoutingNodeMapper.class), mock(RoutingEdgeMapper.class), mock(MaterialMapper.class), mock(RoutingReferenceRules.class), mock(MesMaterialIssueMapper.class), mock(WorkOrderExtraMapper.class), executions, mock(WorkorderErpFacts.class), mock(BusinessDirectoryService.class), mock(MesWorkorderMaterialMapper.class), Mockito.mock(BusinessSourceRules.class), Mockito.mock(WorkorderErpExtraMapper.class), Mockito.mock(SyncCoordinator.class), Mockito.mock(MaterialQuantityRules.class), Mockito.mock(AutoCodeNumbering.class), Mockito.mock(PlatformTransactionManager.class));
        assertEquals(ErrorCode.EXECUTION_REFERENCED, assertThrows(FoundationException.class, () -> service.delete(1L, 0)).code());
        var input = new WorkOrderModels.Save("WO", 2L, null, 1L, null, null, null, null, null, 0, null);
        assertEquals(ErrorCode.EXECUTION_FROZEN, assertThrows(FoundationException.class, () -> service.update(1L, input)).code());
        verify(orders, never()).deleteByPrimaryKey(any());
        verify(orders, never()).updateByPrimaryKey(any());
    }
}
