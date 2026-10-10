package dev.mesbase.server;

import dev.mesbase.common.core.domain.model.LoginUser;
import dev.mesbase.core.factory.FactoryCatalog;
import dev.mesbase.core.factory.FactoryPreference;
import dev.mesbase.infrastructure.catalog.DatabaseFactoryPreference;
import dev.mesbase.system.factory.MfUserPreferenceMapper;
import dev.mesbase.core.security.AuthorizationService;
import dev.mesbase.framework.security.SystemTokenFilter;
import dev.mesbase.framework.sync.SyncCoordinator;
import dev.mesbase.framework.web.service.TokenService;
import dev.mesbase.infrastructure.persistence.PlatformPersistence;
import dev.mesbase.infrastructure.platform.LocalPrivateFileStorage;
import dev.mesbase.infrastructure.platform.MinioPrivateFileStorage;
import dev.mesbase.platform.management.FileStorage;
import dev.mesbase.platform.security.PlatformAuthorization;
import dev.mesbase.core.security.AuthenticatedActor;
import dev.mesbase.core.security.Permission;
import dev.mesbase.quartz.service.ISysJobService;
import dev.mesbase.quartz.service.impl.SysJobServiceImpl;
import dev.mesbase.quartz.util.ControlledJobRunner;
import dev.mesbase.system.service.ISysConfigService;
import dev.mesbase.system.service.ISysDictTypeService;
import dev.mesbase.system.service.ISysUserService;
import dev.mesbase.system.service.SystemPermissionService;
import java.nio.file.Path;
import java.util.Properties;
import java.util.Set;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.impl.StdSchedulerFactory;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.core.env.Environment;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "mes.system.enabled", havingValue = "true")
public class SystemConfiguration {

    @Bean
    FactoryPreference persistentFactoryPreference(MfUserPreferenceMapper preferences) {
        return new DatabaseFactoryPreference(preferences);
    }

    @Bean
    SystemTokenFilter systemTokens(TokenService t) {
        return new SystemTokenFilter(t);
    }

    @Bean
    FilterRegistrationBean<SystemTokenFilter> systemTokenRegistration(SystemTokenFilter f) {
        var r = new FilterRegistrationBean<>(f);
        r.setEnabled(false);
        return r;
    }

    @Bean
    ControlledJobRunner controlledJobs(PlatformPersistence u, FactoryCatalog f) {
        return new ControlledJobRunner(u, f);
    }

    @Bean(destroyMethod = "shutdown")
    Scheduler systemScheduler(Environment env) throws SchedulerException {
        var props = new Properties();
        props.setProperty("org.quartz.scheduler.instanceName", "MESBaseSystem");
        props.setProperty("org.quartz.threadPool.threadCount", env.getProperty("spring.quartz.properties.org.quartz.threadPool.threadCount", "2"));
        return new StdSchedulerFactory(props).getScheduler();
    }

    @Bean
    SmartInitializingSingleton warmSystemCache(ISysDictTypeService dicts, ISysConfigService configs) {
        return () -> {
            dicts.loadingDictCache();
            configs.loadingConfigCache();
        };
    }

    @Bean
    SmartInitializingSingleton startSystemJobs(ISysJobService jobs, Scheduler scheduler) {
        return () -> {
            try {
                ((SysJobServiceImpl) jobs).init();
                scheduler.start();
            } catch (Exception failure) {
                throw new IllegalStateException("Quartz initialization failed", failure);
            }
        };
    }

    @Bean
    FileStorage systemFileStorage(Environment e) {
        String kind = e.getRequiredProperty("mes.storage.type");
        if ("minio".equals(kind))
            return new MinioPrivateFileStorage(e.getRequiredProperty("minio.url"), e.getRequiredProperty("minio.access-key"), e.getRequiredProperty("minio.secret-key"), e.getRequiredProperty("minio.bucket-name"));
        if (!"local".equals(kind)) throw new IllegalArgumentException("Unsupported private storage type");
        return new LocalPrivateFileStorage(Path.of(e.getRequiredProperty("mes.platform.file-root")));
    }

    @Bean
    SyncCoordinator syncCoordinator(PlatformPersistence p, FactoryCatalog f) {
        return new SyncCoordinator(p, f);
    }

    @Bean
    PasswordEncoder passwords() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    UserDetailsService systemAccounts(ISysUserService users) {
        return name -> {
            var user = users.selectUserByUserName(name);
            if (user == null || !"0".equals(user.getStatus()) || !"0".equals(user.getDelFlag()))
                throw new UsernameNotFoundException("Account not found");
            return new LoginUser(user.getUserId(), user.getDeptId(), user, Set.of());
        };
    }

    @Bean
    AuthorizationService factoryAuthorization(SystemPermissionService p) {
        return (actor, scope, permission) -> p.grantedFactory(actor.id(), scope.factoryId().value(), permission.code());
    }

    @Bean
    PlatformAuthorization platformAuthorization(SystemPermissionService p) {
        return new PlatformAuthorization() {
            public boolean hasPermission(AuthenticatedActor actor, Permission permission) {
                return p.grantedPlatform(actor.id(), permission.code());
            }

            public boolean hasPlatformRole(AuthenticatedActor actor) {
                return p.hasPlatformRole(actor.id());
            }
        };
    }
}
