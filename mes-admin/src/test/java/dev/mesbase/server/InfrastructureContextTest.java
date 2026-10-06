package dev.mesbase.server;

import dev.mesbase.common.core.redis.SystemCacheNamespace;
import dev.mesbase.core.security.AuthorizationService;
import dev.mesbase.framework.openapi.OpenApiAuthenticationFilter;
import dev.mesbase.framework.openapi.OpenApiHttp;
import dev.mesbase.framework.openapi.OpenApiRateLimiter;
import dev.mesbase.framework.openapi.OpenApiRequestFilter;
import dev.mesbase.framework.openapi.OpenApiSecurityConfig;
import dev.mesbase.framework.openapi.OpenApiTokenService;
import dev.mesbase.framework.openapi.OpenApiWebConfig;
import dev.mesbase.framework.security.SystemTokenFilter;
import dev.mesbase.framework.web.service.TokenService;
import dev.mesbase.infrastructure.persistence.OwnerMapperCatalog;
import dev.mesbase.infrastructure.persistence.PersistenceOwner;
import dev.mesbase.infrastructure.persistence.RuntimeRegistry;
import dev.mesbase.platform.web.RequestBoundaryFilter;
import dev.mesbase.quartz.service.impl.SysJobServiceImpl;
import dev.mesbase.system.openapi.service.OpenApiClientService;
import dev.mesbase.system.service.ISysConfigService;
import dev.mesbase.system.service.ISysDictTypeService;
import dev.mesbase.system.service.ISysUserService;
import dev.mesbase.system.service.SystemPermissionService;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.quartz.Scheduler;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * TEST ONLY production infrastructure with H2 pools and mocked business/Redis dependencies.
 */
class InfrastructureContextTest {
    @TempDir
    Path temporary;

    @TestConfiguration(proxyBeanMethods = false)
    @EnableWebMvc
    @EnableWebSecurity
    @EnableAspectJAutoProxy(exposeProxy = true)
    static class FixtureDependencies {
        @Bean
        ISysUserService users() {
            return mock(ISysUserService.class);
        }

        @Bean
        SystemPermissionService permissions() {
            return mock(SystemPermissionService.class);
        }

        @Bean
        ISysDictTypeService dictionaries() {
            return mock(ISysDictTypeService.class);
        }

        @Bean
        ISysConfigService configs() {
            return mock(ISysConfigService.class);
        }

        @Bean
        SysJobServiceImpl jobs() {
            return mock(SysJobServiceImpl.class);
        }

        @Bean
        TokenService tokens() {
            return mock(TokenService.class);
        }

        @Bean
        OpenApiClientService clients() {
            return mock(OpenApiClientService.class);
        }

        @Bean
        RedisConnectionFactory redisConnections() {
            return mock(RedisConnectionFactory.class);
        }

        @Bean
        StringRedisTemplate strings(RedisConnectionFactory connections) {
            return new StringRedisTemplate(connections);
        }
    }

    @Test
    void productionInfrastructureCreatesThreeOwnersTwoSecurityChainsAndClosesQuartz() throws Exception {
        Scheduler scheduler;
        try (var context = new AnnotationConfigWebApplicationContext()) {
            context.setServletContext(new MockServletContext());
            for (var source : new YamlPropertySourceLoader().load(
                    "defaults", new ClassPathResource("application.yml"))) {
                context.getEnvironment().getPropertySources().addLast(source);
            }
            context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("TEST ONLY", Map.ofEntries(
                    Map.entry("mes.system.enabled", true),
                    Map.entry("mes.platform.enabled", true),
                    Map.entry("mes.platform.migrate", false),
                    Map.entry("mes.platform.url", "jdbc:h2:mem:" + UUID.randomUUID()),
                    Map.entry("mes.platform.username", "sa"),
                    Map.entry("mes.platform.password", ""),
                    Map.entry("mes.platform.file-root", temporary.toString()),
                    Map.entry("mes.runtime.enabled", true),
                    Map.entry("mes.runtime.datasource-key", Base64.getEncoder().encodeToString(new byte[32])),
                    Map.entry("mes.erp.enabled", true),
                    Map.entry("mes.erp.datasources.erp-main.url", "jdbc:h2:mem:" + UUID.randomUUID()),
                    Map.entry("mes.erp.datasources.erp-main.username", "sa"),
                    Map.entry("mes.erp.datasources.erp-main.password", ""),
                    Map.entry("mes.erp.datasources.erp-main.driver-class-name", "org.h2.Driver"))));
            context.register(FixtureDependencies.class, FoundationConfiguration.class,
                    PersistenceConfiguration.class, FactoryRuntimeConfiguration.class,
                    SystemConfiguration.class, SystemRedisConfiguration.class,
                    OpenApiSecurityConfig.class, OpenApiWebConfig.class, OpenApiTokenService.class,
                    OpenApiRateLimiter.class, OpenApiHttp.class, SystemCacheNamespace.class,
                    RequestBoundaryFilter.class);
            context.refresh();

            assertEquals(3, context.getBeansOfType(DataSource.class).size());
            assertEquals(3, context.getBeansOfType(SqlSessionFactory.class).size());
            assertEquals(3, context.getBeansOfType(PlatformTransactionManager.class).size());
            assertEquals(1, context.getBeansOfType(UserDetailsService.class).size());
            assertEquals(1, context.getBeansOfType(AuthorizationService.class).size());
            assertEquals(1, context.getBeansOfType(RuntimeRegistry.class).size());
            assertTrue(context.containsBean("sysUserMapper"));
            assertTrue(context.getBean(OwnerMapperCatalog.class).types(PersistenceOwner.Kind.BUSINESS).isEmpty());

            var chains = context.getBeansOfType(SecurityFilterChain.class);
            assertEquals(2, chains.size());
            assertEquals(1, chains.get("security").getFilters().stream()
                    .filter(SystemTokenFilter.class::isInstance).count());
            assertEquals(1, chains.get("openApiSecurity").getFilters().stream()
                    .filter(OpenApiRequestFilter.class::isInstance).count());
            assertEquals(1, chains.get("openApiSecurity").getFilters().stream()
                    .filter(OpenApiAuthenticationFilter.class::isInstance).count());
            var registration = context.getBean("systemTokenRegistration", FilterRegistrationBean.class);
            assertFalse(registration.isEnabled());
            assertSame(context.getBean(SystemTokenFilter.class), registration.getFilter());
            assertTrue(context.getBean("xss", FilterRegistrationBean.class).isEnabled());
            assertEquals(1, context.getBeansOfType(RequestBoundaryFilter.class).size());
            verifyNoInteractions(context.getBean(ISysUserService.class),
                    context.getBean(SystemPermissionService.class), context.getBean(OpenApiClientService.class));

            scheduler = context.getBean(Scheduler.class);
            assertTrue(scheduler.isStarted());
        }
        assertTrue(scheduler.isShutdown());
    }
}
