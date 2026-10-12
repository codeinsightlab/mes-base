package dev.mesbase.server;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.mesbase.common.core.domain.entity.SysUser;
import dev.mesbase.common.core.domain.model.LoginUser;
import dev.mesbase.common.core.redis.SystemCacheNamespace;
import dev.mesbase.core.factory.FactoryCatalog;
import dev.mesbase.core.security.AuthenticatedActor;
import dev.mesbase.core.security.Permission;
import jakarta.servlet.http.HttpServletRequest;
import dev.mesbase.core.factory.FactoryPreference;
import dev.mesbase.core.factory.FactorySelectionService;
import dev.mesbase.framework.openapi.OpenApiCatalogService;
import dev.mesbase.framework.openapi.OpenApiDiagnosticsController;
import dev.mesbase.framework.openapi.OpenApiDiagnosticsService;
import dev.mesbase.framework.openapi.OpenApiExceptionResolver;
import dev.mesbase.framework.openapi.OpenApiHttp;
import dev.mesbase.framework.openapi.OpenApiRateLimiter;
import dev.mesbase.framework.openapi.OpenApiResponseAdvice;
import dev.mesbase.framework.openapi.OpenApiSecurityConfig;
import dev.mesbase.framework.openapi.OpenApiTokenController;
import dev.mesbase.framework.openapi.OpenApiTokenService;
import dev.mesbase.framework.openapi.OpenApiWebConfig;
import dev.mesbase.framework.security.SystemTokenFilter;
import dev.mesbase.framework.web.service.TokenService;
import dev.mesbase.infrastructure.persistence.PlatformPersistence;
import dev.mesbase.platform.security.PlatformAuthorization;
import dev.mesbase.platform.web.FoundationAccessInterceptor;
import dev.mesbase.platform.web.FoundationErrorAdvice;
import dev.mesbase.platform.web.RequestBoundaryFilter;
import dev.mesbase.system.openapi.mapper.OpenApiClientApiMapper;
import dev.mesbase.system.openapi.mapper.OpenApiClientMapper;
import dev.mesbase.system.openapi.service.OpenApiClientService;
import dev.mesbase.web.controller.system.OpenApiClientController;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.MapPropertySource;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * TEST ONLY: real Spring security/services/XML and private Redis, H2 center database, synthetic internal identity.
 */
@EnabledIfSystemProperty(named = "mes.test.redis", matches = "true")
class OpenApiRestorationTest {
    @Configuration
    @EnableWebMvc
    @EnableWebSecurity
    @EnableTransactionManagement
    static class Web implements WebMvcConfigurer {
        @Bean
        @Order(1)
        SecurityFilterChain internalSecurity(HttpSecurity http) throws Exception {
            var tokens = mock(TokenService.class);
            var user = new SysUser();
            user.setUserId(100L);
            user.setUserName("TEST_ONLY_ADMIN");
            when(tokens.getLoginUser(any())).thenAnswer(call -> {
                var request = call.getArgument(0, HttpServletRequest.class);
                return "Bearer INTERNAL_TEST_ONLY".equals(request.getHeader("Authorization"))
                        ? new LoginUser(100L, 1L, user, Set.of()) : null;
            });
            return http.csrf(config -> config.disable())
                    .addFilterBefore(new SystemTokenFilter(tokens), UsernamePasswordAuthenticationFilter.class)
                    .authorizeHttpRequests(config -> config.anyRequest().authenticated())
                    .exceptionHandling(config -> config.authenticationEntryPoint((request, response, failure) -> response.setStatus(401)))
                    .build();
        }

        @Override
        public void addInterceptors(InterceptorRegistry registry) {
            var selection = new FactorySelectionService(mock(FactoryCatalog.class), mock(FactoryPreference.class));
            PlatformAuthorization platform = new PlatformAuthorization() {
                public boolean hasPermission(AuthenticatedActor actor, Permission permission) {
                    return true;
                }

                public boolean hasPlatformRole(AuthenticatedActor actor) {
                    return true;
                }
            };
            registry.addInterceptor(new FoundationAccessInterceptor(selection, (actor, scope, permission) -> false, platform));
        }
    }

    @Test
    void restoredClientAdministrationTokensGrantsAndIndependentChainsWorkTogether() throws Exception {
        int port;
        try (var socket = new ServerSocket(0)) {
            port = socket.getLocalPort();
        }
        var directory = Files.createTempDirectory("mes-openapi-test-");
        var process = new ProcessBuilder("/opt/homebrew/bin/redis-server", "--bind", "127.0.0.1", "--port", String.valueOf(port),
                "--save", "", "--appendonly", "no", "--dir", directory.toString())
                .redirectErrorStream(true).redirectOutput(directory.resolve("redis.log").toFile()).start();
        var connections = new LettuceConnectionFactory("127.0.0.1", port);
        try (var context = new AnnotationConfigWebApplicationContext()) {
            connections.afterPropertiesSet();
            connections.start();
            var redis = new StringRedisTemplate(connections);
            boolean ready = false;
            for (int i = 0; i < 50 && !ready; i++) {
                try (var connection = connections.getConnection()) {
                    ready = "PONG".equals(connection.ping());
                } catch (RuntimeException waiting) {
                    Thread.sleep(20);
                }
            }
            assertTrue(ready);
            var source = new DriverManagerDataSource("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
            var jdbc = new JdbcTemplate(source);
            jdbc.execute("CREATE TABLE open_api_client(client_id BIGINT AUTO_INCREMENT PRIMARY KEY,app_key VARCHAR(64),secret_hash VARCHAR(100),"
                    + "client_name VARCHAR(100),status INT,credential_version BIGINT,create_by VARCHAR(64),create_time TIMESTAMP,"
                    + "update_by VARCHAR(64),update_time TIMESTAMP,remark VARCHAR(500))");
            jdbc.execute("CREATE TABLE open_api_client_api(id BIGINT AUTO_INCREMENT PRIMARY KEY,client_id BIGINT,api_code VARCHAR(100),"
                    + "create_by VARCHAR(64),create_time TIMESTAMP)");
            var persistence = new PlatformPersistence(source, Set.of(OpenApiClientMapper.class, OpenApiClientApiMapper.class));
            context.setServletContext(new MockServletContext());
            context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("TEST ONLY", Map.of(
                    "mes.system.enabled", true, "open-api.enabled", true)));
            context.addBeanFactoryPostProcessor(beans -> {
                beans.registerSingleton("strings", redis);
                beans.registerSingleton("namespace", new SystemCacheNamespace(new MockEnvironment().withProperty("mes.platform.url", source.getUrl())));
                beans.registerSingleton("masterTransactionManager", persistence.transactionManager());
                beans.registerSingleton("clients", persistence.mapper(OpenApiClientMapper.class));
                beans.registerSingleton("grants", persistence.mapper(OpenApiClientApiMapper.class));
                beans.registerSingleton("encoder", PasswordEncoderFactories.createDelegatingPasswordEncoder());
            });
            context.register(Web.class, OpenApiSecurityConfig.class, OpenApiWebConfig.class, OpenApiHttp.class,
                    OpenApiTokenService.class, OpenApiRateLimiter.class, OpenApiClientService.class,
                    OpenApiCatalogService.class, OpenApiDiagnosticsService.class, OpenApiTokenController.class,
                    OpenApiDiagnosticsController.class, OpenApiClientController.class, OpenApiExceptionResolver.class,
                    OpenApiResponseAdvice.class, FoundationErrorAdvice.class);
            context.refresh();
            assertEquals(2, context.getBeansOfType(SecurityFilterChain.class).size());
            MockMvc http = MockMvcBuilders.webAppContextSetup(context)
                    .addFilters(new RequestBoundaryFilter(), context.getBean(FilterChainProxy.class)).build();
            var json = new ObjectMapper();
            var created = http.perform(post("/api/system/open-api/clients").header("Authorization", "Bearer INTERNAL_TEST_ONLY")
                            .contentType("application/json").content("{\"clientName\":\"TEST ONLY\"}"))
                    .andExpect(status().isOk()).andReturn();
            var data = json.readTree(created.getResponse().getContentAsString()).path("data");
            long id = data.path("clientId").asLong();
            String credentials = json.writeValueAsString(Map.of("appKey", data.path("appKey")
                    .asText(), "appSecret", data.path("appSecret").asText()));
            http.perform(post("/open-api/token").contentType("application/json").content(credentials))
                    .andExpect(status().isUnauthorized());
            http.perform(put("/api/system/open-api/clients/status").header("Authorization", "Bearer INTERNAL_TEST_ONLY")
                            .contentType("application/json").content("{\"clientId\":" + id + ",\"status\":1}"))
                    .andExpect(status().isOk());
            var issued = http.perform(post("/open-api/token").contentType("application/json").content(credentials))
                    .andExpect(status().isOk()).andReturn();
            String token = json.readTree(issued.getResponse().getContentAsString()).path("data").path("accessToken")
                    .asText();
            assertTrue(token.startsWith("oa_"));
            http.perform(get("/open-api/v1/diagnostics/ping").header("Authorization", "Bearer " + token))
                    .andExpect(status().isForbidden());
            http.perform(put("/api/system/open-api/clients/apis").header("Authorization", "Bearer INTERNAL_TEST_ONLY")
                            .contentType("application/json")
                            .content("{\"clientId\":" + id + ",\"apiCodes\":[\"diagnostics.ping\"]}"))
                    .andExpect(status().isOk());
            http.perform(get("/open-api/v1/diagnostics/ping").header("Authorization", "Bearer " + token)
                            .header("X-Factory-Id", "IGNORED"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.data.message").value("pong"));
            http.perform(get("/open-api/v1/diagnostics/client").header("Authorization", "Bearer " + token))
                    .andExpect(status().isForbidden());
            http.perform(get("/open-api/v1/diagnostics/ping").header("Authorization", "Bearer INTERNAL_TEST_ONLY"))
                    .andExpect(status().isUnauthorized());
            http.perform(get("/api/system/open-api/clients").header("Authorization", "Bearer " + token))
                    .andExpect(status().isUnauthorized());
            http.perform(get("/api/system/open-api/clients").header("Authorization", "Bearer INTERNAL_TEST_ONLY"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.data.list[0].secretHash").doesNotExist());
            http.perform(get("/api/system/open-api/clients/api-catalog").header("Authorization", "Bearer INTERNAL_TEST_ONLY"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.data[0].apiCode").exists());
            http.perform(put("/api/system/open-api/clients/apis").header("Authorization", "Bearer INTERNAL_TEST_ONLY")
                            .contentType("application/json").content("{\"clientId\":" + id + ",\"apiCodes\":[]}"))
                    .andExpect(status().isOk());
            http.perform(get("/open-api/v1/diagnostics/ping").header("Authorization", "Bearer " + token))
                    .andExpect(status().isForbidden());
            http.perform(post("/open-api/token/revoke").header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk());
            http.perform(get("/open-api/v1/diagnostics/ping").header("Authorization", "Bearer " + token))
                    .andExpect(status().isUnauthorized());
            http.perform(put("/api/system/open-api/clients/apis").header("Authorization", "Bearer INTERNAL_TEST_ONLY")
                            .contentType("application/json")
                            .content("{\"clientId\":" + id + ",\"apiCodes\":[\"diagnostics.ping\"]}"))
                    .andExpect(status().isOk());
            var again = http.perform(post("/open-api/token").contentType("application/json").content(credentials))
                    .andExpect(status().isOk()).andReturn();
            String nextToken = json.readTree(again.getResponse().getContentAsString()).path("data").path("accessToken")
                    .asText();
            http.perform(put("/api/system/open-api/clients/status").header("Authorization", "Bearer INTERNAL_TEST_ONLY")
                            .contentType("application/json").content("{\"clientId\":" + id + ",\"status\":0}"))
                    .andExpect(status().isOk());
            http.perform(get("/open-api/v1/diagnostics/ping").header("Authorization", "Bearer " + nextToken))
                    .andExpect(status().isUnauthorized());
            http.perform(put("/api/system/open-api/clients/status").header("Authorization", "Bearer INTERNAL_TEST_ONLY")
                            .contentType("application/json").content("{\"clientId\":" + id + ",\"status\":1}"))
                    .andExpect(status().isOk());
            var reenabled = http.perform(post("/open-api/token").contentType("application/json").content(credentials))
                    .andExpect(status().isOk()).andReturn();
            String currentToken = json.readTree(reenabled.getResponse().getContentAsString()).path("data")
                    .path("accessToken").asText();
            http.perform(get("/open-api/v1/diagnostics/ping").header("Authorization", "Bearer " + nextToken))
                    .andExpect(status().isUnauthorized());
            http.perform(post("/api/system/open-api/clients/reset-secret").header("Authorization", "Bearer INTERNAL_TEST_ONLY")
                    .contentType("application/json").content("{\"clientId\":" + id + "}")).andExpect(status().isOk());
            http.perform(get("/open-api/v1/diagnostics/ping").header("Authorization", "Bearer " + currentToken))
                    .andExpect(status().isUnauthorized());
            http.perform(post("/open-api/token").contentType("application/json").content(credentials))
                    .andExpect(status().isUnauthorized());
        } finally {
            connections.destroy();
            process.destroy();
            if (!process.waitFor(5, TimeUnit.SECONDS)) process.destroyForcibly();
        }
    }
}
