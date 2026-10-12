package dev.mesbase.framework.openapi;

import dev.mesbase.common.openapi.OpenApiDataScopeIgnore;
import dev.mesbase.common.openapi.OpenApiError;
import dev.mesbase.common.openapi.OpenApiException;
import dev.mesbase.common.openapi.OpenApiPrincipal;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.method.HandlerMethod;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * TEST ONLY: generic policy switch, no MES route/entity/database fixture.
 */
class OpenApiDataScopeVerificationTest {
    private final OpenApiPrincipal principal = new OpenApiPrincipal(42L, "TEST_ONLY", 7L);
    private final MockHttpServletRequest request = new MockHttpServletRequest("GET", "/open-api/v1/test");

    static class Endpoint {
        public void business() {
        }

        @OpenApiDataScopeIgnore
        public void diagnostic() {
        }
    }

    private HandlerMethod endpoint(String method) throws Exception {
        return new HandlerMethod(new Endpoint(), Endpoint.class.getMethod(method));
    }

    private OpenApiProperties enabled() {
        var properties = new OpenApiProperties();
        properties.getDataScope().setEnabled(true);
        return properties;
    }

    @Test
    void disabledBaseDoesNotRequirePolicyOrInventADataScope() throws Exception {
        var verification = new OpenApiDataScopeVerification(new OpenApiProperties(), List.of());
        assertFalse(verification.isEnabled());
        assertDoesNotThrow(() -> verification.verify(principal, "test.business", request, endpoint("business")));
    }

    @Test
    void disabledBaseNeverCallsAnInstalledPolicy() throws Exception {
        var count = new AtomicInteger();
        var verification = new OpenApiDataScopeVerification(new OpenApiProperties(), List.of((actor, code, http, handler) -> count.incrementAndGet()));
        verification.verify(principal, "test.business", request, endpoint("business"));
        assertEquals(0, count.get());
    }

    @Test
    void enabledWithoutPolicyFailsBeforeServingRequests() {
        assertThrows(IllegalStateException.class, () -> new OpenApiDataScopeVerification(enabled(), List.of()));
    }

    @Test
    void enabledWithAmbiguousPolicyFailsBeforeServingRequests() {
        OpenApiDataScopeValidator validator = (actor, code, http, handler) -> {
        };
        assertThrows(IllegalStateException.class, () -> new OpenApiDataScopeVerification(enabled(), List.of(validator, validator)));
    }

    @Test
    void installedPolicyReceivesVerifiedExternalIdentityAndActualOperation() throws Exception {
        var count = new AtomicInteger();
        var method = endpoint("business");
        var verification = new OpenApiDataScopeVerification(enabled(), List.of((actor, code, http, handler) -> {
            assertSame(principal, actor);
            assertEquals("test.business", code);
            assertSame(request, http);
            assertSame(method, handler);
            count.incrementAndGet();
        }));
        verification.verify(principal, "test.business", request, method);
        assertTrue(verification.isEnabled());
        assertEquals(1, count.get());
    }

    @Test
    void policyRejectionIsPreservedAsScopeError() throws Exception {
        var rejection = new OpenApiException(OpenApiError.DATA_SCOPE_NOT_CONFIGURED);
        var verification = new OpenApiDataScopeVerification(enabled(), List.of((actor, code, http, handler) -> {
            throw rejection;
        }));
        assertSame(rejection, assertThrows(OpenApiException.class, () -> verification.verify(principal, "test.business", request, endpoint("business"))));
    }

    @Test
    void explicitDiagnosticExemptionDoesNotRunBusinessPolicy() throws Exception {
        var verification = new OpenApiDataScopeVerification(enabled(), List.of((actor, code, http, handler) -> fail("Diagnostics do not read business data")));
        assertDoesNotThrow(() -> verification.verify(principal, "diagnostics.ping", request, endpoint("diagnostic")));
    }
}
