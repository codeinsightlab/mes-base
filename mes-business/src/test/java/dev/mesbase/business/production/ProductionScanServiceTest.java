package dev.mesbase.business.production;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.mesbase.business.production.controller.ProductionScanController;
import dev.mesbase.business.production.dao.MesProductionScanActivityMapper;
import dev.mesbase.business.production.domain.MesProductionScanActivityEntity;
import dev.mesbase.business.production.domain.ProductionIdentity;
import dev.mesbase.business.production.domain.ProductionScanInputMethod;
import dev.mesbase.business.production.domain.ProductionScanOutcome;
import dev.mesbase.business.production.domain.ProductionScanType;
import dev.mesbase.business.production.service.ProductionIdentityService;
import dev.mesbase.business.production.service.ProductionScanService;
import dev.mesbase.common.utils.SecurityUtils;
import dev.mesbase.core.error.ErrorCode;
import dev.mesbase.core.error.FoundationException;
import dev.mesbase.core.factory.FactoryId;
import dev.mesbase.core.factory.FactoryScope;
import dev.mesbase.core.security.AuthenticatedActor;
import dev.mesbase.core.security.AuthorizationService;
import dev.mesbase.core.security.Permission;
import dev.mesbase.platform.execution.FactoryContext;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.SimpleTransactionStatus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ProductionScanServiceTest {
    private final ProductionIdentityService identities = mock(ProductionIdentityService.class);
    private final AuthorizationService authorization = mock(AuthorizationService.class);
    private final MesProductionScanActivityMapper activities = mock(MesProductionScanActivityMapper.class);
    private final PlatformTransactionManager manager = mock(PlatformTransactionManager.class);
    private final FactoryScope scope = new FactoryScope(new FactoryId("A"));
    private final ProductionIdentity identity = new ProductionIdentity(9007199254740993L, "WO-1:000P#000LOT",
            1L, "WO-1", 2L, "000P", "产品", 3L, "R1", 1, "RUNNING", new BigDecimal("100.000000"), "000LOT");
    private MockedStatic<SecurityUtils> security;
    private ProductionScanService service;

    @BeforeEach
    void setup() {
        FactoryContext.bind(scope);
        security = mockStatic(SecurityUtils.class);
        security.when(SecurityUtils::getUserId).thenReturn(17L);
        var authentication = mock(Authentication.class);
        when(authentication.getName()).thenReturn("scan-user");
        security.when(SecurityUtils::getAuthentication).thenReturn(authentication);
        when(authorization.hasPermission(any(), any(), any())).thenReturn(true);
        when(manager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        when(activities.insert(any())).thenReturn(1);
        service = new ProductionScanService(identities, authorization, activities, manager);
    }

    @AfterEach
    void cleanup() {
        security.close();
        FactoryContext.clear();
    }

    @Test
    void everySceneUsesSameResolverAndCurrentScenePermissionAndSeparateAuditTransaction() {
        when(identities.resolve("exact-code")).thenReturn(identity);
        for (var type : ProductionScanType.values()) {
            assertSame(identity, service.scan(type, "exact-code", ProductionScanInputMethod.IMAGE, "server-request"));
            verify(authorization).hasPermission(new AuthenticatedActor("scan-user"), scope, new Permission(type.getPermission()));
        }
        var definitions = ArgumentCaptor.forClass(TransactionDefinition.class);
        verify(manager, times(3)).getTransaction(definitions.capture());
        assertTrue(definitions.getAllValues().stream()
                .allMatch(d -> d.getPropagationBehavior() == TransactionDefinition.PROPAGATION_REQUIRES_NEW));
        var captured = ArgumentCaptor.forClass(MesProductionScanActivityEntity.class);
        verify(activities, times(3)).insert(captured.capture());
        for (var event : captured.getAllValues()) {
            assertEquals(17L, event.getUserId());
            assertEquals(identity.executionId(), event.getExecutionId());
            assertEquals(ProductionScanOutcome.SUCCESS.getValue(), event.getOutcome());
            assertEquals(ProductionScanInputMethod.IMAGE.getValue(), event.getInputMethod());
            assertEquals("server-request", event.getRequestId());
            assertNull(event.getErrorCode());
            assertTrue(event.getDurationMs() >= 0);
        }
    }

    @Test
    void deniedSceneNeverResolvesAndRecordsSafeFailureWithoutChangingException() {
        when(authorization.hasPermission(any(), any(), any())).thenReturn(false);
        var rejected = assertThrows(FoundationException.class,
                () -> service.scan(ProductionScanType.REPORTING, "private-code", ProductionScanInputMethod.TEXT, "trace"));
        assertEquals(ErrorCode.FORBIDDEN, rejected.code());
        verifyNoInteractions(identities);
        var captured = ArgumentCaptor.forClass(MesProductionScanActivityEntity.class);
        verify(activities).insert(captured.capture());
        assertEquals("FORBIDDEN", captured.getValue().getErrorCode());
        assertEquals(ProductionScanOutcome.FAILURE.getValue(), captured.getValue().getOutcome());
        assertNull(captured.getValue().getExecutionId());
    }

    @Test
    void resolverAndAuditFailuresKeepOriginalOutcomeAndDoNotStoreUnsafeTrace() {
        var unavailable = new IllegalStateException("TEST ONLY infrastructure failure");
        when(identities.resolve("same-code")).thenThrow(unavailable);
        when(activities.insert(any())).thenThrow(new IllegalStateException("TEST ONLY audit database failure"));
        assertSame(unavailable, assertThrows(IllegalStateException.class,
                () -> service.scan(ProductionScanType.EXECUTION_LOOKUP, "same-code", ProductionScanInputMethod.TEXT, "x".repeat(129))));
        var captured = ArgumentCaptor.forClass(MesProductionScanActivityEntity.class);
        verify(activities).insert(captured.capture());
        assertEquals("INTERNAL_ERROR", captured.getValue().getErrorCode());
        assertNull(captured.getValue().getRequestId());
    }

    @Test
    void auditFailureDoesNotTurnSuccessfulResolutionIntoFailure() {
        when(identities.resolve("same-code")).thenReturn(identity);
        when(activities.insert(any())).thenThrow(new IllegalStateException("TEST ONLY audit unavailable"));
        assertSame(identity, service.scan(ProductionScanType.DEFECT_DECLARATION, "same-code", ProductionScanInputMethod.TEXT, null));
    }

    @Test
    void controllerRejectsSpoofedFieldsAndUsesServerTraceAndSafeIdentityConversion() throws Exception {
        var mapper = new ObjectMapper();
        for (var field : List.of("userId", "occurredAt", "outcome", "executionId", "requestId")) {
            String json = "{\"type\":\"REPORTING\",\"code\":\"exact-code\",\"inputMethod\":\"TEXT\",\"" + field + "\":\"spoofed\"}";
            assertThrows(Exception.class, () -> mapper.readValue(json, ProductionScanController.Input.class));
        }
        when(identities.resolve("exact-code")).thenReturn(identity);
        var controller = new ProductionScanController(service);
        var request = new MockHttpServletRequest();
        request.addHeader("X-Request-Id", "client-spoof");
        request.setAttribute("mes.requestId", "trusted-server-trace");
        var view = controller.scan(new ProductionScanController.Input("REPORTING", "exact-code", "TEXT"), request);
        assertEquals("9007199254740993", view.executionId());
        assertEquals("000LOT", view.batchNo());
        var captured = ArgumentCaptor.forClass(MesProductionScanActivityEntity.class);
        verify(activities).insert(captured.capture());
        assertEquals("trusted-server-trace", captured.getValue().getRequestId());
        assertThrows(FoundationException.class,
                () -> controller.scan(new ProductionScanController.Input("INVALID", "exact-code", "TEXT"), request));
    }
}
