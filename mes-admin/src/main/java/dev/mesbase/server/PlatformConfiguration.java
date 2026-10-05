package dev.mesbase.server;

import org.springframework.context.annotation.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.core.userdetails.*;
import org.springframework.beans.factory.SmartInitializingSingleton;
import dev.mesbase.platform.management.*;
import dev.mesbase.platform.security.*;
import dev.mesbase.core.factory.*;
import dev.mesbase.core.security.AuthorizationService;
import dev.mesbase.infrastructure.platform.*;
import dev.mesbase.infrastructure.persistence.*;

import javax.sql.DataSource;
import java.time.*;
import java.util.*;

@Configuration
@org.springframework.context.annotation.Conditional(dev.mesbase.server.LegacyPlatformCondition.class)
@ConditionalOnProperty(name = "mes.platform.enabled", havingValue = "true")
public class PlatformConfiguration {
    @Bean(destroyMethod = "close")
    DataSource platformDataSource(Environment env) {
        return DruidPools.create(DruidPools.settings(env), "platform", env.getRequiredProperty("mes.platform.url"), env.getRequiredProperty("mes.platform.username"), env.getRequiredProperty("mes.platform.password"));
    }

    @Bean
    PlatformPersistence platformPersistence(DataSource platformDataSource, Environment env, org.springframework.beans.factory.ObjectProvider<PlatformMapperRegistration> registrations) {
        if (env.getProperty("mes.platform.migrate", Boolean.class, false))
            org.flywaydb.core.Flyway.configure().dataSource(platformDataSource).locations("classpath:db/platform").load().migrate();
        var mappers = new HashSet<Class<?>>();
        mappers.add(PlatformMapper.class);
        registrations.orderedStream().forEach(r -> mappers.add(r.mapper()));
        return new PlatformPersistence(platformDataSource, mappers);
    }

    @Bean
    PlatformStore platformStore(PlatformPersistence persistence) {
        return new PlatformStore(persistence);
    }

    @Bean
    PasswordEncoder passwords() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    PlatformService platformService(PlatformStore store, FactoryCatalog catalog, PasswordEncoder passwords) {
        return new PlatformService(store, catalog, passwords);
    }

    @Bean
    SessionService sessions(PlatformStore store, PasswordEncoder passwords, Environment env) {
        return new SessionService(store, passwords, Clock.systemUTC(), Duration.ofSeconds(env.getProperty("mes.platform.session-ttl-seconds", Long.class, 3600L)));
    }

    @Bean
    PlatformTokenFilter tokenFilter(SessionService sessions) {
        return new PlatformTokenFilter(sessions);
    }

    @Bean
    org.springframework.boot.web.servlet.FilterRegistrationBean<PlatformTokenFilter> tokenFilterRegistration(PlatformTokenFilter filter) {
        var registration = new org.springframework.boot.web.servlet.FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    @Primary
    UserDetailsService platformAccounts(PlatformStore store) {
        return username -> store.run(m -> {
            var u = m.userByUsername(username);
            if (u == null) throw new UsernameNotFoundException("Account not found");
            return User.withUsername(u.username()).password(u.passwordHash()).disabled(u.status() != PlatformRows.Status.ENABLED).authorities(List.of()).build();
        });
    }

    @Bean
    @Primary
    AuthorizationService factoryAuthorization(PlatformService service) {
        return (actor, scope, permission) -> service.granted(actor.id(), scope.factoryId().value(), permission.code());
    }

    @Bean
    PlatformAuthorization platformAuthorization(PlatformService service) {
        return (actor, permission) -> service.granted(actor.id(), null, permission.code());
    }

    @Bean(destroyMethod = "close")
    JobService jobs(PlatformStore store, FactoryCatalog catalog) throws org.quartz.SchedulerException {
        return new JobService(store, catalog, Map.of("platform-heartbeat", new JobService.RegisteredHandler(PlatformRows.JobScope.PLATFORM, () -> {
        }), "factory-heartbeat", new JobService.RegisteredHandler(PlatformRows.JobScope.FACTORY, () -> dev.mesbase.platform.execution.FactoryContext.required())));
    }

    @Bean
    SmartInitializingSingleton startJobs(JobService jobs) {
        return jobs::start;
    }

    @Bean
    FileService files(PlatformStore store, Environment env) {
        return new FileService(store, java.nio.file.Path.of(env.getRequiredProperty("mes.platform.file-root")), env.getProperty("mes.platform.max-upload-bytes", Long.class, 10_000_000L));
    }

    @Bean
    org.springframework.web.servlet.config.annotation.WebMvcConfigurer platformAudit(PlatformService service) {
        return new org.springframework.web.servlet.config.annotation.WebMvcConfigurer() {
            public void addInterceptors(org.springframework.web.servlet.config.annotation.InterceptorRegistry registry) {
                registry.addInterceptor(new PlatformAuditInterceptor(service));
            }
        };
    }
}
