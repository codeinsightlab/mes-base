package dev.mesbase;

import dev.mesbase.core.factory.FactorySelectionService;
import dev.mesbase.core.security.AuthorizationService;
import dev.mesbase.core.security.AuthenticatedActor;
import dev.mesbase.core.security.Permission;
import dev.mesbase.core.factory.FactoryId;
import dev.mesbase.core.factory.FactoryScope;
import dev.mesbase.core.error.FoundationException;
import dev.mesbase.platform.execution.FactoryContext;
import dev.mesbase.platform.security.PlatformAuthorization;
import dev.mesbase.platform.web.EndpointAccess;
import dev.mesbase.platform.web.EndpointCatalog;
import dev.mesbase.platform.web.EndpointScopePolicy;
import dev.mesbase.platform.web.FoundationAccessInterceptor;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.method.HandlerMethod;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class EndpointScopePolicyTest {
    @AfterEach
    void clean() {
        FactoryContext.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    void prefixesUsePathSegmentsAndSharedEndpointsCannotBecomeAdministration() throws Exception {
        for (String prefix : List.of("system", "tool", "monitor")) {
            assertTrue(EndpointScopePolicy.platformManagement("/api/" + prefix + "/entry"));
        }
        assertFalse(EndpointScopePolicy.platformManagement("/api/systematic/entry"));
        assertEquals(EndpointAccess.PermissionScope.FACTORY, EndpointScopePolicy.scope("/api/business/system/entry"));
        assertThrows(IllegalStateException.class, () -> EndpointCatalog.required(handler("business"), "/api/system/entry"));
        assertThrows(IllegalStateException.class, () -> EndpointCatalog.required(handler("platform"), "/api/pro/entry"));
        assertThrows(IllegalStateException.class, () -> EndpointCatalog.required(handler("platform"), "/api/personal/entry"));
        EndpointCatalog.required(handler("personal"), "/api/personal/profile");
    }

    @Test
    void platformRoleAndPermissionAreIndependentAndFactoryHeaderNeverSelectsPlatformDatabase() throws Exception {
        var selection = mock(FactorySelectionService.class);
        var business = mock(AuthorizationService.class);
        var grants = new PlatformAuthorization() {
            public boolean hasPlatformRole(AuthenticatedActor actor) {
                return actor.id().startsWith("platform");
            }

            public boolean hasPermission(AuthenticatedActor actor, Permission permission) {
                return actor.id().equals("platform-granted");
            }
        };
        var interceptor = new FoundationAccessInterceptor(selection, business, grants);
        var request = new MockHttpServletRequest("GET", "/api/monitor/entry");
        request.addHeader("X-Factory-Id", "unknown factory metadata");
        var response = new MockHttpServletResponse();
        login("factory-only");
        assertThrows(FoundationException.class, () -> interceptor.preHandle(request, response, handler("platform")));
        login("platform-empty");
        assertThrows(FoundationException.class, () -> interceptor.preHandle(request, response, handler("platform")));
        login("platform-granted");
        FactoryContext.bind(new FactoryScope(new FactoryId("OLD")));
        assertTrue(interceptor.preHandle(request, response, handler("platform")));
        assertTrue(FactoryContext.current().isEmpty());
        request.removeHeader("X-Factory-Id");
        assertTrue(interceptor.preHandle(request, response, handler("platform")));
        request.setRequestURI("/api/personal/profile");
        login("factory-only");
        assertTrue(interceptor.preHandle(request, response, handler("personal")));
        request.setRequestURI("/api/system/catalog");
        assertThrows(FoundationException.class, () -> interceptor.preHandle(request, response, handler("personal")));
        verifyNoInteractions(selection, business);
    }

    private static void login(String actor) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(actor, "TEST ONLY", List.of()));
    }

    private static HandlerMethod handler(String name) throws NoSuchMethodException {
        return new HandlerMethod(new Endpoints(), Endpoints.class.getMethod(name));
    }

    public static class Endpoints {
        @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED, permission = "monitor:entry:list",
                permissionScope = EndpointAccess.PermissionScope.PLATFORM)
        public void platform() {
        }

        @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED, permission = "business:entry:read")
        public void business() {
        }

        @EndpointAccess(value = EndpointAccess.Kind.AUTHENTICATED,
                permissionScope = EndpointAccess.PermissionScope.PLATFORM)
        public void personal() {
        }
    }
}
