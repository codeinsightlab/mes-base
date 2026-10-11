package dev.mesbase.server;

import dev.mesbase.core.error.ErrorCode;
import dev.mesbase.core.error.FoundationException;
import dev.mesbase.core.factory.Factory;
import dev.mesbase.core.factory.FactoryCatalog;
import dev.mesbase.core.factory.FactoryId;
import dev.mesbase.core.factory.FactorySelectionService;
import dev.mesbase.core.factory.FactoryStatus;
import dev.mesbase.core.security.AuthenticatedActor;
import dev.mesbase.core.security.Permission;
import dev.mesbase.infrastructure.catalog.InMemoryFactoryPreference;
import dev.mesbase.platform.execution.FactoryContext;
import dev.mesbase.platform.security.PlatformAuthorization;
import dev.mesbase.platform.web.EndpointAccess;
import dev.mesbase.platform.web.FoundationAccessInterceptor;
import dev.mesbase.platform.web.FoundationErrorAdvice;
import dev.mesbase.platform.web.RequestBoundaryFilter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Real HTTP handlers with runtime admission installed, without external JDBC or Redis.
 */
class FoundationInitializationTest {
    private final List<Factory> factories = new ArrayList<>();
    private final AtomicInteger admissions = new AtomicInteger();

    private MockMvc http(String username) {
        if (username != null) {
            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken(username, "unused", List.of()));
        }
        FactoryCatalog catalog = new FactoryCatalog() {
            public Optional<Factory> find(FactoryId id) {
                return factories.stream().filter(factory -> factory.id().equals(id)).findFirst();
            }

            public List<Factory> enabled(int offset, int limit) {
                return factories.stream().filter(factory -> factory.status() == FactoryStatus.ENABLED)
                        .skip(offset).limit(limit).toList();
            }
        };
        var selection = new FactorySelectionService(catalog, new InMemoryFactoryPreference());
        PlatformAuthorization platform = new PlatformAuthorization() {
            public boolean hasPermission(AuthenticatedActor actor, Permission permission) {
                return actor.id().equals("platform");
            }

            public boolean hasPlatformRole(AuthenticatedActor actor) {
                return actor.id().equals("platform");
            }
        };
        var access = new FoundationAccessInterceptor(selection, (actor, scope, permission) -> true,
                platform, scope -> {
            admissions.incrementAndGet();
            if (!scope.factoryId().value().equals("bound")) {
                throw new FoundationException(ErrorCode.RUNTIME_NOT_CONFIGURED);
            }
        });
        return MockMvcBuilders.standaloneSetup(new FoundationController(new FoundationService(selection)),
                        new ProbeController())
                .setControllerAdvice(new FoundationErrorAdvice()).addInterceptors(access)
                .addFilters(new RequestBoundaryFilter()).build();
    }

    private void addFactory(String id) {
        factories.add(new Factory(new FactoryId(id), "TEST ONLY", id, id, FactoryStatus.ENABLED));
    }

    @AfterEach
    void cleanup() {
        assertTrue(FactoryContext.current().isEmpty());
        SecurityContextHolder.clearContext();
    }

    @Test
    void emptyRoleAccountCanDiscoverEmptyFactoriesAndContextWithAdmissionInstalled() throws Exception {
        var http = http("empty-role");
        http.perform(get("/api/foundation/factories")).andExpect(status().isOk())
                .andExpect(content().json("[]"));
        http.perform(get("/api/foundation/context")).andExpect(status().isOk())
                .andExpect(content().json("{\"factoryId\":null}"));
        assertEquals(0, admissions.get());
    }

    @Test
    void platformAccountCanReachPlatformConfigurationWithoutFactories() throws Exception {
        var http = http("platform");
        http.perform(get("/api/foundation/context")).andExpect(status().isOk());
        http.perform(get("/api/system/test-only-initialization")).andExpect(status().isOk());
        assertEquals(0, admissions.get());
    }

    @Test
    void factorySelectionDoesNotRequireDatasourceAndCanRecoverFromEmptyState() throws Exception {
        var http = http("empty-role");
        http.perform(get("/api/foundation/context")).andExpect(status().isOk());
        addFactory("unbound");
        http.perform(put("/api/foundation/preference").principal(
                                SecurityContextHolder.getContext().getAuthentication())
                        .contentType("application/json").content("{\"factoryId\":\"unbound\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.factoryId").value("unbound"));
        http.perform(get("/api/foundation/context")).andExpect(status().isOk())
                .andExpect(jsonPath("$.factoryId").value("unbound"));
        http.perform(get("/api/foundation/access")).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("RUNTIME_NOT_CONFIGURED"));
        addFactory("bound");
        http.perform(get("/api/foundation/access").header("X-Factory-Id", "bound"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.factoryId").value("bound"));
    }

    @Test
    void missingFactoryStillRejectsBusinessAuthorization() throws Exception {
        http("empty-role").perform(get("/api/foundation/access"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("FACTORY_SCOPE_REQUIRED"));
        assertEquals(0, admissions.get());
    }

    @Test
    void discoveryStillRequiresAuthenticationAndCannotGrantPlatformAccess() throws Exception {
        http(null).perform(get("/api/foundation/context")).andExpect(status().isUnauthorized());
        http("empty-role").perform(get("/api/system/test-only-initialization"))
                .andExpect(status().isForbidden());
    }

    @Test
    void authenticatedBusinessEndpointStillRunsBindingAdmissionWhenFactoryIsSelected() throws Exception {
        addFactory("unbound");
        http("empty-role").perform(get("/api/test-only/business")
                        .header("X-Factory-Id", "unbound"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("RUNTIME_NOT_CONFIGURED"));
        assertEquals(1, admissions.get());
    }

    @RestController
    static class ProbeController {
        @GetMapping("/api/system/test-only-initialization")
        @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED, permission = "system:factory:list",
                permissionScope = EndpointAccess.PermissionScope.PLATFORM)
        String platform() {
            return "TEST ONLY";
        }

        @GetMapping("/api/test-only/business")
        @EndpointAccess(EndpointAccess.Kind.AUTHENTICATED)
        String business() {
            return "TEST ONLY";
        }
    }
}
