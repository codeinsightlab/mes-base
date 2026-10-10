package dev.mesbase.server;

import com.alibaba.druid.pool.DruidDataSource;
import dev.MesBaseApplication;
import dev.mesbase.core.factory.Factory;
import dev.mesbase.core.factory.FactoryCatalog;
import dev.mesbase.core.factory.FactoryId;
import dev.mesbase.core.factory.FactoryStatus;
import dev.mesbase.core.security.AuthenticatedActor;
import dev.mesbase.core.security.AuthorizationService;
import dev.mesbase.core.security.Permission;
import dev.mesbase.core.security.Role;
import dev.mesbase.infrastructure.catalog.InMemoryFactoryCatalog;
import dev.mesbase.infrastructure.security.InMemoryRoleAuthorization;
import dev.mesbase.platform.security.PlatformAuthorization;
import dev.mesbase.server.managed.ManagedDatasourceService;
import dev.mesbase.server.monitor.DruidMonitorService;
import dev.mesbase.web.controller.monitor.DruidController;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Set;
import java.util.UUID;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * TEST ONLY accounts/H2; real embedded HTTP chain and production monitor Controller/Service.
 */
@SpringBootTest(classes = {MesBaseApplication.class, DruidMonitorHttpTest.Fixture.class}, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"mes.system.enabled=false", "mes.platform.enabled=false", "mes.runtime.enabled=false"})
class DruidMonitorHttpTest {
    @TestConfiguration
    static class Fixture {
        @Bean(destroyMethod = "close")
        DruidDataSource testMonitorPool() throws Exception {
            var pool = new DruidDataSource();
            pool.setName("TEST_ONLY_master");
            pool.setUrl("jdbc:h2:mem:" + UUID.randomUUID());
            pool.setInitialSize(0);
            pool.setMinIdle(0);
            pool.setValidationQuery("SELECT 1");
            pool.setFilters("stat");
            pool.init();
            return pool;
        }

        @Bean
        DruidController testMonitorController(DruidDataSource pool) {
            return new DruidController(pool, new DruidMonitorService(pool, (ManagedDatasourceService) null, null));
        }

        @Bean
        @Primary
        FactoryCatalog testFactories() {
            return new InMemoryFactoryCatalog(List.of(new Factory(new FactoryId("F"), "TEST_ONLY", "F", "TEST_ONLY", FactoryStatus.ENABLED)));
        }

        @Bean
        @Primary
        AuthorizationService testFactoryGrants() {
            var grants = new InMemoryRoleAuthorization();
            grants.replace(new AuthenticatedActor("factory-reader"), List.of(new Role("TEST_ONLY", new FactoryId("F"), Set.of(new Permission("monitor:druid:list")))));
            return grants;
        }

        @Bean
        PlatformAuthorization testPlatformGrants() {
            return new PlatformAuthorization() {
                public boolean hasPermission(AuthenticatedActor actor, Permission permission) {
                    return "reader".equals(actor.id()) && "monitor:druid:list".equals(permission.code());
                }

                public boolean hasPlatformRole(AuthenticatedActor actor) {
                    return "reader".equals(actor.id());
                }
            };
        }

        @Bean
        @Primary
        UserDetailsService testAccounts() {
            return new InMemoryUserDetailsManager(
                    User.withUsername("reader").password("{noop}TEST_ONLY_password").authorities(List.of()).build(),
                    User.withUsername("visitor").password("{noop}TEST_ONLY_password").authorities(List.of()).build(),
                    User.withUsername("factory-reader").password("{noop}TEST_ONLY_password").authorities(List.of())
                            .build());
        }
    }

    @Autowired
    Environment environment;

    private HttpResponse<String> get(String path, String user) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + environment.getProperty("local.server.port") + path));
        if (user != null) request.header("Authorization", "Basic " + Base64.getEncoder()
                .encodeToString((user + ":TEST_ONLY_password").getBytes(StandardCharsets.UTF_8)));
        return HttpClient.newHttpClient().send(request.GET().build(), HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void bothNewEndpointsRequirePlatformMonitoringPermission() throws Exception {
        for (String path : List.of("/api/monitor/druid/sources", "/api/monitor/druid/snapshot?sourceId=platform")) {
            assertEquals(401, get(path, null).statusCode());
            assertEquals(403, get(path, "visitor").statusCode());
            assertEquals(403, get(path, "factory-reader").statusCode());
            var allowed = get(path, "reader");
            assertEquals(200, allowed.statusCode(), allowed.body());
        }
    }

    @Test
    void unknownPoolAndInvalidBoundsReturnSafeRealHttpErrors() throws Exception {
        var unknown = get("/api/monitor/druid/snapshot?sourceId=business:999", "reader");
        assertEquals(404, unknown.statusCode(), unknown.body());
        assertTrue(unknown.body().contains("NOT_FOUND"));
        assertEquals(400, get("/api/monitor/druid/sources?pageSize=101", "reader").statusCode());
        assertEquals(400, get("/api/monitor/druid/snapshot?sourceId=platform&limit=0", "reader").statusCode());
    }
}
