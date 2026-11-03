package dev.mesbase.business.production;

import dev.mesbase.business.wm.dao.WmStockDocumentMapper;
import dev.mesbase.business.production.dao.MesProductionExecutionMapper;
import dev.mesbase.business.production.dao.WorkorderMapper;
import dev.mesbase.business.production.domain.MesProductionExecutionEntity;
import dev.mesbase.business.production.domain.WorkorderEntity;
import dev.mesbase.business.production.service.ProductionChainLocks;
import dev.mesbase.core.error.ErrorCode;
import dev.mesbase.core.error.FoundationException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class ProductionChainLocksTest {
    private final WorkorderMapper orders = mock(WorkorderMapper.class);
    private final MesProductionExecutionMapper executions = mock(MesProductionExecutionMapper.class);
    private final ProductionChainLocks locks = new ProductionChainLocks(orders, executions,
            mock(WmStockDocumentMapper.class));

    private MesProductionExecutionEntity execution(long order) {
        var row = new MesProductionExecutionEntity();
        row.setId(17L);
        row.setExecutionCode("17");
        row.setWorkorderId(order);
        return row;
    }

    @Test
    void locksParentBeforeChildAndReturnsTheLockedCurrentRow() {
        var before = execution(11L);
        var current = execution(11L);
        current.setStatus("RUNNING");
        when(executions.selectByCode("17")).thenReturn(before);
        when(orders.selectByPrimaryKeyForUpdate(11L)).thenReturn(new WorkorderEntity());
        when(executions.selectByCodeForUpdate("17")).thenReturn(current);
        assertSame(current, locks.execution("17"));
        var ordered = inOrder(executions, orders);
        ordered.verify(executions).selectByCode("17");
        ordered.verify(orders).selectByPrimaryKeyForUpdate(11L);
        ordered.verify(executions).selectByCodeForUpdate("17");
        verifyNoMoreInteractions(orders, executions);
    }

    @Test
    void rejectsParentChangedWhileWaitingForTheLock() {
        when(executions.selectByCode("17")).thenReturn(execution(11L));
        when(orders.selectByPrimaryKeyForUpdate(11L)).thenReturn(new WorkorderEntity());
        when(executions.selectByCodeForUpdate("17")).thenReturn(execution(12L));
        assertEquals(ErrorCode.RESOURCE_CONFLICT,
                assertThrows(FoundationException.class, () -> locks.execution("17")).code());
    }

    @Test
    void rejectsExecutionDeletedBetweenDiscoveryAndLocking() {
        when(executions.selectByCode("17")).thenReturn(execution(11L));
        when(orders.selectByPrimaryKeyForUpdate(11L)).thenReturn(new WorkorderEntity());
        assertEquals(ErrorCode.EXECUTION_NOT_FOUND,
                assertThrows(FoundationException.class, () -> locks.execution("17")).code());
    }
}
