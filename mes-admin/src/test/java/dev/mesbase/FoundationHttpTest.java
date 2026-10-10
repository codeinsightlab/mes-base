package dev.mesbase;

import dev.MesBaseApplication;
import dev.mesbase.core.error.ErrorCode;
import dev.mesbase.core.error.FoundationException;
import dev.mesbase.core.factory.Factory;
import dev.mesbase.core.factory.FactoryCatalog;
import dev.mesbase.core.factory.FactoryId;
import dev.mesbase.core.factory.FactoryStatus;
import dev.mesbase.core.security.AuthenticatedActor;
import dev.mesbase.core.security.Permission;
import dev.mesbase.core.security.Role;
import dev.mesbase.infrastructure.catalog.InMemoryFactoryCatalog;
import dev.mesbase.infrastructure.security.InMemoryRoleAuthorization;
import dev.mesbase.platform.execution.FactoryContext;
import dev.mesbase.platform.web.EndpointAccess;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.core.env.Environment;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * TEST ONLY accounts and failure endpoint. Actual embedded Tomcat and HTTP security chain.
 */
@SpringBootTest(classes = {MesBaseApplication.class, FoundationHttpTest.Fixture.class, FoundationHttpTest.FailureController.class},
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"mes.system.enabled=false", "mes.platform.enabled=false", "mes.runtime.enabled=false"})
class FoundationHttpTest {
    @TestConfiguration
    static class Fixture {
        @Bean
        @Primary
        FactoryCatalog testFactories() {
            return new InMemoryFactoryCatalog(List.of(
                    new Factory(new FactoryId("A"), "synthetic-enterprise", "A", "TEST ONLY A", FactoryStatus.ENABLED),
                    new Factory(new FactoryId("B"), "synthetic-enterprise", "B", "TEST ONLY B", FactoryStatus.ENABLED),
                    new Factory(new FactoryId("C"), "synthetic-enterprise", "C", "TEST ONLY disabled", FactoryStatus.DISABLED)));
        }

        @Bean
        @Primary
        InMemoryRoleAuthorization testAuthorization() {
            var auth = new InMemoryRoleAuthorization();
            grantReader(auth);
            return auth;
        }

        @Bean
        @Primary
        UserDetailsService testAccounts() {
            return new InMemoryUserDetailsManager(
                    User.withUsername("visitor").password("{noop}test-only-valid-password").authorities(List.of())
                            .build(),
                    User.withUsername("preference-user").password("{noop}test-only-valid-password")
                            .authorities(List.of()).build(),
                    User.withUsername("reader").password("{noop}test-only-valid-password").authorities(List.of())
                            .build(),
                    User.withUsername("disabled").password("{noop}test-only-valid-password").authorities(List.of())
                            .disabled(true).build());
        }
    }

    static void grantReader(InMemoryRoleAuthorization auth) {
        auth.replace(new AuthenticatedActor("reader"), List.of(new Role("test-only-role-A", new FactoryId("A"), Set.of(new Permission("foundation:access:read")))));
    }

    @RestController
    static class FailureController {
        @GetMapping("/test-only/failure/{kind}")
        @EndpointAccess(EndpointAccess.Kind.AUTHENTICATED)
        String fail(@PathVariable String kind) {
            if (kind.equals("programming-error"))
                throw new IllegalArgumentException("TEST ONLY internal programming failure");
            if (kind.equals("conflict")) throw new FoundationException(ErrorCode.CONFLICT);
            if (kind.equals("invalid-state")) throw new FoundationException(ErrorCode.UNPROCESSABLE);
            throw new IllegalStateException("TEST ONLY raw SQL SELECT password FROM private_table; internal /private/path jdbc:test://sensitive-host", new IllegalStateException("TEST ONLY original cause"));
        }

        @GetMapping("/test-only/scope-state")
        @EndpointAccess(EndpointAccess.Kind.AUTHENTICATED)
        String scope() {
            return FactoryContext.current().map(s -> s.factoryId().value()).orElse("NONE");
        }
    }

    @Autowired
    Environment environment;
    @Autowired
    InMemoryRoleAuthorization authorization;
    private final HttpClient client = HttpClient.newHttpClient();

    private HttpResponse<String> request(String path, String actor, String factory, String method,
                                         String body) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + environment.getProperty("local.server.port") + path))
                .timeout(Duration.ofSeconds(10));
        if (actor != null) builder.header("Authorization", "Basic " + Base64.getEncoder()
                .encodeToString((actor + ":test-only-valid-password").getBytes(StandardCharsets.UTF_8)));
        if (factory != null) builder.header("X-Factory-Id", factory);
        if (body != null) builder.header("Content-Type", "application/json");
        return client.send(builder.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body))
                .build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> get(String path, String actor, String factory) throws Exception {
        return request(path, actor, factory, "GET", null);
    }

    private void error(HttpResponse<String> response, int status, String code) {
        assertEquals(status, response.statusCode(), response.body());
        assertTrue(response.body().contains("\"errorCode\":\"" + code + "\""), response.body());
        String id = response.headers().firstValue("X-Request-Id").orElseThrow();
        UUID.fromString(id);
        assertTrue(response.body().contains(id));
    }

    @Test
    void publicWorksAndAnonymousAuthenticatedIs401() throws Exception {
        assertEquals(200, get("/health", null, null).statusCode());
        error(get("/api/foundation/context", null, null), 401, "UNAUTHENTICATED");
    }

    @Test
    void nonMvcUnknownAndStaticSurfacesRequireAuthentication() throws Exception {
        error(get("/not-a-route", null, null), 401, "UNAUTHENTICATED");
        error(get("/test-only/static.js", null, null), 401, "UNAUTHENTICATED");
        error(request("/health", null, null, "POST", null), 401, "UNAUTHENTICATED");
        assertEquals(200, get("/health", null, null).statusCode());
    }

    @Test
    void emptyRoleAccountAuthenticatesAndEntersEitherEnabledScope() throws Exception {
        var a = get("/api/foundation/context", "visitor", "A");
        var b = get("/api/foundation/context", "visitor", "B");
        assertEquals(200, a.statusCode());
        assertEquals(200, b.statusCode());
        assertTrue(a.body().contains("A"));
        assertTrue(b.body().contains("B"));
        assertEquals(200, get("/api/foundation/factories", "visitor", null).statusCode());
    }

    @Test
    void authenticatedCanHaveNoFactoryWithoutImplicitDefault() throws Exception {
        var response = get("/api/foundation/context", "visitor", null);
        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("null"));
        error(get("/api/foundation/access", "visitor", null), 400, "FACTORY_SCOPE_REQUIRED");
    }

    @Test
    void authorizedRequiresFactoryPermission() throws Exception {
        error(get("/api/foundation/access", "visitor", "A"), 403, "FORBIDDEN");
        assertEquals(200, get("/api/foundation/access", "reader", "A").statusCode());
        error(get("/api/foundation/access", "reader", "B"), 403, "FORBIDDEN");
    }

    @Test
    void preferenceIsUxOnlyExplicitFactoryWinsAndUnknownFieldsAreRejected() throws Exception {
        assertEquals(200, request("/api/foundation/preference", "preference-user", null, "PUT", "{\"factoryId\":\"A\"}").statusCode());
        assertTrue(get("/api/foundation/context", "preference-user", null).body().contains("A"));
        assertTrue(get("/api/foundation/context", "preference-user", "B").body().contains("B"));
        error(get("/api/foundation/access", "preference-user", null), 403, "FORBIDDEN");
        error(request("/api/foundation/preference", "preference-user", null, "PUT", "{\"factoryId\":\"A\",\"userId\":\"reader\"}"), 400, "BAD_REQUEST");
    }

    @Test
    void invalidScopeUnknownScopeAndDisabledScopeUseRealErrors() throws Exception {
        error(get("/api/foundation/context", "visitor", "bad factory"), 400, "BAD_REQUEST");
        error(get("/api/foundation/context", "visitor", "unknown"), 404, "FACTORY_NOT_FOUND");
        error(get("/api/foundation/context", "visitor", "C"), 422, "FACTORY_DISABLED");
    }

    @Test
    void accountsAreAuthenticatedIndependentlyFromRoles() throws Exception {
        error(get("/api/foundation/context", "unknown-account", "A"), 401, "UNAUTHENTICATED");
        error(get("/api/foundation/context", "disabled", "A"), 401, "UNAUTHENTICATED");
    }

    @Test
    void removedPlaceholderErpEndpointIsNotPublished() throws Exception {
        assertEquals(404, get("/api/foundation/erp/connection", "reader", "A").statusCode());
    }

    @Test
    void permissionsAreNotCachedInUserSessionAndRevocationTakesEffect() throws Exception {
        try {
            assertEquals(200, get("/api/foundation/access", "reader", "A").statusCode());
            authorization.replace(new AuthenticatedActor("reader"), List.of());
            error(get("/api/foundation/access", "reader", "A"), 403, "FORBIDDEN");
        } finally {
            grantReader(authorization);
        }
    }

    @Test
    void concurrentSameActorFactoryPermissionsDoNotBleed() throws Exception {
        try (var executor = Executors.newFixedThreadPool(2)) {
            var a = executor.submit(() -> get("/api/foundation/access", "reader", "A"));
            var b = executor.submit(() -> get("/api/foundation/access", "reader", "B"));
            assertEquals(200, a.get(10, TimeUnit.SECONDS).statusCode());
            error(b.get(10, TimeUnit.SECONDS), 403, "FORBIDDEN");
        }
    }

    @Test
    void errorsAreSafe400404409422500AndRequestScopeIsCleanAfterFailure() throws Exception {
        error(get("/api/foundation/factories?limit=101", "visitor", null), 400, "BAD_REQUEST");
        error(get("/not-a-route", "visitor", null), 404, "NOT_FOUND");
        error(get("/test-only/failure/conflict", "visitor", "A"), 409, "CONFLICT");
        error(get("/test-only/failure/invalid-state", "visitor", "B"), 422, "UNPROCESSABLE");
        var internal = get("/test-only/failure/raw", "visitor", "A");
        error(internal, 500, "INTERNAL_ERROR");
        for (String privateText : List.of("SELECT", "private_table", "private/path", "sensitive-host", "original cause"))
            assertFalse(internal.body().contains(privateText));
        assertEquals("NONE", get("/test-only/scope-state", "visitor", null).body());
    }

    @Test
    void wrongPasswordIs401AndInternalArgumentFailureIs500() throws Exception {
        var uri = URI.create("http://127.0.0.1:" + environment.getProperty("local.server.port") + "/api/foundation/context");
        var request = HttpRequest.newBuilder(uri).header("Authorization", "Basic " + Base64.getEncoder()
                .encodeToString("visitor:test-only-wrong-password".getBytes(StandardCharsets.UTF_8))).GET().build();
        error(client.send(request, HttpResponse.BodyHandlers.ofString()), 401, "UNAUTHENTICATED");
        error(get("/test-only/failure/programming-error", "visitor", "A"), 500, "INTERNAL_ERROR");
    }

    @Test
    void clientRequestIdCannotControlServerCorrelation() throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + environment.getProperty("local.server.port") + "/health"))
                .header("X-Request-Id", "test-only-client-id").GET().build();
        var response = client.send(request, HttpResponse.BodyHandlers.ofString());
        assertNotEquals("test-only-client-id", response.headers().firstValue("X-Request-Id").orElseThrow());
    }
}
