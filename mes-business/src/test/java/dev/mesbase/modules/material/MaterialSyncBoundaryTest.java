package dev.mesbase.modules.material;

import dev.mesbase.core.error.ErrorCode;
import dev.mesbase.core.error.FoundationException;
import dev.mesbase.core.factory.FactoryId;
import dev.mesbase.core.factory.FactoryScope;
import dev.mesbase.framework.sync.SyncCoordinator;
import dev.mesbase.modules.material.sync.MaterialQueryPort;
import dev.mesbase.modules.material.sync.MaterialSyncService;
import dev.mesbase.modules.material.sync.MaterialSyncWriter;
import dev.mesbase.modules.material.sync.TestMaterialQueryAdapter;
import dev.mesbase.modules.material.sync.UnconfiguredMaterialQueryAdapter;
import dev.mesbase.platform.execution.FactoryContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class MaterialSyncBoundaryTest {
    private final FactoryScope scope = new FactoryScope(new FactoryId("TEST_ONLY"));

    @AfterEach
    void clear() {
        FactoryContext.clear();
    }

    @Test
    void placeholderExplicitlyFailsWithoutData() {
        var failure = assertThrows(FoundationException.class, () -> new UnconfiguredMaterialQueryAdapter().query(scope, 1000));
        assertEquals(ErrorCode.ERP_SYNC_NOT_CONFIGURED, failure.code());
        assertEquals("暂未接入 ERP 物料查询接口", failure.getMessage());
    }

    @Test
    void noPortBeanUsesFallbackAndNeverCallsMaterialWriter() {
        var beans = new DefaultListableBeanFactory();
        var coordinator = mock(SyncCoordinator.class);
        var writer = mock(MaterialSyncWriter.class);
        var run = new SyncCoordinator.Run(scope, "codegen.material", "TEST_ONLY_RUN");
        when(coordinator.claim(eq(scope), eq("codegen.material"), any())).thenReturn(run);
        FactoryContext.bind(scope);
        var failure = assertThrows(FoundationException.class, () -> new MaterialSyncService(
                beans.getBeanProvider(MaterialQueryPort.class), coordinator, writer).sync(scope));
        assertEquals("暂未接入 ERP 物料查询接口", failure.getMessage());
        verifyNoInteractions(writer, coordinator);
    }

    @Test
    void suppliedPortReplacesFallbackAndLostRunStopsBeforeWrite() {
        var beans = new DefaultListableBeanFactory();
        beans.registerSingleton("test", new TestMaterialQueryAdapter());
        var coordinator = mock(SyncCoordinator.class);
        var writer = mock(MaterialSyncWriter.class);
        var run = new SyncCoordinator.Run(scope, "codegen.material", "TEST_ONLY_RUN");
        when(coordinator.claim(eq(scope), eq("codegen.material"), any())).thenReturn(run);
        doThrow(new FoundationException(ErrorCode.SYNC_EXECUTION_LOST)).when(coordinator).progress(eq(run), anyLong());
        doThrow(new FoundationException(ErrorCode.SYNC_EXECUTION_LOST)).when(coordinator).fail(run);
        FactoryContext.bind(scope);
        var failure = assertThrows(FoundationException.class, () -> new MaterialSyncService(
                beans.getBeanProvider(MaterialQueryPort.class), coordinator, writer).sync(scope));
        assertEquals(ErrorCode.SYNC_EXECUTION_LOST, failure.code());
        assertEquals(1, failure.getSuppressed().length);
        verifyNoInteractions(writer);
    }
}
