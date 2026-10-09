package dev.mesbase.framework.sync;

import dev.mesbase.core.error.ErrorCode;
import dev.mesbase.core.error.FoundationException;
import dev.mesbase.core.factory.Factory;
import dev.mesbase.core.factory.FactoryCatalog;
import dev.mesbase.core.factory.FactoryId;
import dev.mesbase.core.factory.FactoryScope;
import dev.mesbase.core.factory.FactoryStatus;
import dev.mesbase.infrastructure.persistence.PlatformPersistence;
import dev.mesbase.system.sync.MasterSync;
import dev.mesbase.system.sync.MasterSyncMapper;
import java.time.Duration;
import java.util.Optional;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Coordinator control-flow unit tests; not JDBC or multi-node evidence.
 */
class SyncCoordinatorTest {
    private final PlatformPersistence persistence = mock(PlatformPersistence.class);
    private final MasterSyncMapper mapper = mock(MasterSyncMapper.class);
    private final FactoryCatalog factories = mock(FactoryCatalog.class);
    private final FactoryScope scope = new FactoryScope(new FactoryId("TEST_ONLY"));
    private SyncCoordinator coordinator;

    @BeforeEach
    void setUp() {
        when(persistence.mapper(MasterSyncMapper.class)).thenReturn(mapper);
        when(persistence.run(any())).thenAnswer(call -> {
            Supplier<?> work = call.getArgument(0);
            return work.get();
        });
        when(factories.find(scope.factoryId())).thenReturn(Optional.of(factory(FactoryStatus.ENABLED)));
        coordinator = new SyncCoordinator(persistence, factories);
    }

    private Factory factory(FactoryStatus status) {
        return new Factory(scope.factoryId(), "TEST_ONLY", "TEST_ONLY", "TEST ONLY", status);
    }

    private MasterSync state(String status, String token) {
        return new MasterSync(1, scope.factoryId().value(), "test", status, token,
                null, null, null, null, null, 0, null);
    }

    private void rejects(ErrorCode code, Runnable action) {
        assertEquals(code, assertThrows(FoundationException.class, action::run).code());
    }

    @Test
    void invalidScopeAndLeaseAreRejectedBeforeDatabaseWrites() {
        rejects(ErrorCode.FACTORY_SCOPE_REQUIRED, () -> coordinator.claim(null, "test", Duration.ofSeconds(60)));
        rejects(ErrorCode.BAD_REQUEST, () -> coordinator.claim(scope, "test", Duration.ZERO));
        rejects(ErrorCode.BAD_REQUEST, () -> coordinator.claim(scope, "test", Duration.ofSeconds(86401)));
        rejects(ErrorCode.BAD_REQUEST, () -> coordinator.claim(scope, "invalid key", Duration.ofSeconds(60)));
        verifyNoInteractions(mapper);
    }

    @Test
    void disabledFactoryCannotClaim() {
        when(factories.find(scope.factoryId())).thenReturn(Optional.of(factory(FactoryStatus.DISABLED)));
        rejects(ErrorCode.FACTORY_DISABLED, () -> coordinator.claim(scope, "test", Duration.ofSeconds(60)));
        verifyNoInteractions(mapper);
    }

    @Test
    void activeLeaseRejectsWithoutAttemptingAnotherClaim() {
        when(mapper.find("TEST_ONLY", "test")).thenReturn(state("RUNNING", "old"));
        rejects(ErrorCode.SYNC_RUNNING, () -> coordinator.claim(scope, "test", Duration.ofSeconds(60)));
        verify(mapper).recover("TEST_ONLY", "test", "old", 60);
        verify(mapper, never()).claim(anyString(), anyString(), anyString());
    }

    @Test
    void lostConditionalClaimIsBusy() {
        when(mapper.find("TEST_ONLY", "test")).thenReturn(state("SUCCEEDED", "old"));
        rejects(ErrorCode.SYNC_RUNNING, () -> coordinator.claim(scope, "test", Duration.ofSeconds(60)));
    }

    @Test
    void recoveryClaimsWithANewToken() {
        when(mapper.find("TEST_ONLY", "test")).thenReturn(state("RUNNING", "old"));
        when(mapper.recover("TEST_ONLY", "test", "old", 60)).thenReturn(1);
        when(mapper.claim(eq("TEST_ONLY"), eq("test"), anyString())).thenReturn(1);
        var run = coordinator.claim(scope, "test", Duration.ofSeconds(60));
        assertNotEquals("old", run.runToken());
        verify(mapper).claim("TEST_ONLY", "test", run.runToken());
    }

    @Test
    void oldTokenCannotProgressSucceedOrFail() {
        var run = new SyncCoordinator.Run(scope, "test", "old");
        rejects(ErrorCode.SYNC_EXECUTION_LOST, () -> coordinator.progress(run, 10));
        rejects(ErrorCode.SYNC_EXECUTION_LOST, () -> coordinator.succeed(run, null));
        rejects(ErrorCode.SYNC_EXECUTION_LOST, () -> coordinator.fail(run));
        verify(mapper).progress("TEST_ONLY", "test", "old", 10);
        verify(mapper).finish("TEST_ONLY", "test", "old", "SUCCEEDED", null, null);
        verify(mapper).finish("TEST_ONLY", "test", "old", "FAILED", null,
                "同步失败，请通过运行标识查阅后台日志");
    }

    @Test
    void restoredOwnerRejectsTheOldExecutor() {
        when(persistence.readOnly(any())).thenReturn(state("RUNNING", "new"));
        rejects(ErrorCode.SYNC_EXECUTION_LOST,
                () -> coordinator.assertOwner(new SyncCoordinator.Run(scope, "test", "old")));
    }

    @Test
    void negativeProgressNeverReachesTheMapper() {
        rejects(ErrorCode.BAD_REQUEST,
                () -> coordinator.progress(new SyncCoordinator.Run(scope, "test", "old"), -1));
        verifyNoInteractions(mapper);
    }
}
