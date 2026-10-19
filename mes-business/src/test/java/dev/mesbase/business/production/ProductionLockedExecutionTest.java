package dev.mesbase.business.production;

import dev.mesbase.business.issue.dao.MesMaterialIssueMapper;
import dev.mesbase.business.production.dao.MesProductionExecutionMapper;
import dev.mesbase.business.production.dao.WorkorderMapper;
import dev.mesbase.business.production.domain.MesProductionExecutionEntity;
import dev.mesbase.business.production.domain.WorkorderEntity;
import dev.mesbase.business.production.service.ProductionChainLocks;
import dev.mesbase.business.production.service.ProductionChainLocks.LockedExecution;
import dev.mesbase.core.error.FoundationException;
import dev.mesbase.core.factory.FactoryId;
import dev.mesbase.core.factory.FactoryScope;
import dev.mesbase.infrastructure.persistence.OwnedTransactionManager;
import dev.mesbase.platform.execution.FactoryContext;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

/**
 * Real Spring/JDBC transaction identity and lifetime tests; mapper locking semantics are tested separately.
 */
class ProductionLockedExecutionTest {
    private final WorkorderMapper orders = mock(WorkorderMapper.class);
    private final MesProductionExecutionMapper executions = mock(MesProductionExecutionMapper.class);
    private final ProductionChainLocks locks = new ProductionChainLocks(orders, executions, mock(MesMaterialIssueMapper.class));
    private OwnedTransactionManager manager;
    private MesProductionExecutionEntity execution;

    @BeforeEach
    void prepare() {
        FactoryContext.bind(new FactoryScope(new FactoryId("A")));
        manager = new OwnedTransactionManager(new DriverManagerDataSource("jdbc:h2:mem:" + UUID.randomUUID(), "sa", ""));
        execution = new MesProductionExecutionEntity();
        execution.setId(17L);
        execution.setWorkorderId(11L);
        var order = new WorkorderEntity();
        order.setId(11L);
        order.setStatus("RELEASED");
        when(executions.selectByPrimaryKey(17L)).thenReturn(execution);
        when(executions.selectByPrimaryKeyForUpdate(17L)).thenReturn(execution);
        when(orders.selectByPrimaryKeyForUpdate(11L)).thenReturn(order);
    }

    @AfterEach
    void clear() {
        FactoryContext.clear();
    }

    @Test
    void noTransactionCannotIssueALockedViewOrTouchMappers() {
        assertThrows(FoundationException.class, () -> locks.lockedExecution(17L, manager));
        verifyNoInteractions(orders, executions);
    }

    @Test
    void requiredCallsShareThePhysicalUnitButCommitExpiresTheView() {
        var captured = new AtomicReference<LockedExecution>();
        new TransactionTemplate(manager).execute(status -> {
            var locked = locks.lockedExecution(17L, manager);
            captured.set(locked);
            assertSame(execution, locked.execution());
            new TransactionTemplate(manager).execute(nested -> {
                assertSame(execution, locked.execution());
                return null;
            });
            locks.requireActiveExecution(17L, locked);
            // The original discovery and current locking read are retained, but the joined guard reuses them.
            verify(executions, times(1)).selectByPrimaryKey(17L);
            verify(executions, times(1)).selectByPrimaryKeyForUpdate(17L);
            verify(orders, times(1)).selectByPrimaryKeyForUpdate(11L);
            return null;
        });
        assertThrows(FoundationException.class, () -> captured.get().execution());
        new TransactionTemplate(manager).execute(status -> {
            assertThrows(FoundationException.class, () -> captured.get().execution());
            return null;
        });
    }

    @Test
    void suspensionAndResumePermanentlyInvalidateTheOldView() {
        new TransactionTemplate(manager).execute(status -> {
            var locked = locks.lockedExecution(17L, manager);
            var nested = new TransactionTemplate(manager);
            nested.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
            nested.execute(inner -> {
                assertThrows(FoundationException.class, locked::verify);
                return null;
            });
            assertThrows(FoundationException.class, locked::verify);
            return null;
        });
    }

    @Test
    void rollbackFactoryChangeAndCrossThreadUseCannotReuseTheView() {
        var captured = new AtomicReference<LockedExecution>();
        new TransactionTemplate(manager).execute(status -> {
            var locked = locks.lockedExecution(17L, manager);
            captured.set(locked);
            status.setRollbackOnly();
            return null;
        });
        assertThrows(FoundationException.class, () -> captured.get().execution());
        new TransactionTemplate(manager).execute(status -> {
            var locked = locks.lockedExecution(17L, manager);
            FactoryContext.bind(new FactoryScope(new FactoryId("B")));
            assertThrows(FoundationException.class, locked::verify);
            FactoryContext.bind(new FactoryScope(new FactoryId("A")));
            assertThrows(FoundationException.class, locked::verify);
            var other = locks.lockedExecution(17L, manager);
            try (var pool = Executors.newSingleThreadExecutor()) {
                assertTrue(pool.submit(() -> {
                    assertThrows(FoundationException.class, other::verify);
                    return true;
                }).get());
            } catch (Exception failure) {
                throw new AssertionError(failure);
            }
            assertThrows(FoundationException.class, other::verify);
            return null;
        });
    }
}
