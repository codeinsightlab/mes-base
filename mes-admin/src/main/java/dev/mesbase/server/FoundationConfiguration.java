package dev.mesbase.server;

import dev.mesbase.core.factory.FactoryScope;
import java.util.function.Consumer;
import java.util.Optional;

import dev.mesbase.core.error.ErrorCode;
import dev.mesbase.core.factory.FactoryCatalog;
import dev.mesbase.server.managed.ManagedDatasourceService;
import org.springframework.beans.factory.ObjectProvider;
import dev.mesbase.core.factory.FactoryPreference;
import dev.mesbase.core.factory.FactorySelectionService;
import dev.mesbase.core.security.AuthorizationService;
import dev.mesbase.framework.security.SystemTokenFilter;
import dev.mesbase.infrastructure.catalog.InMemoryFactoryCatalog;
import dev.mesbase.infrastructure.catalog.InMemoryFactoryPreference;
import dev.mesbase.infrastructure.persistence.BusinessPersistence;
import dev.mesbase.infrastructure.persistence.BusinessPersistenceResolver;
import dev.mesbase.infrastructure.persistence.ConfiguredBusinessPersistenceResolver;
import dev.mesbase.infrastructure.persistence.DedicatedDatabasePersistence;
import dev.mesbase.infrastructure.persistence.RuntimeRegistry;
import dev.mesbase.platform.security.PlatformAuthorization;
import dev.mesbase.platform.web.ErrorWriter;
import dev.mesbase.platform.web.FoundationAccessInterceptor;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import jakarta.servlet.DispatcherType;
import dev.mesbase.platform.web.PublicEndpointMatcher;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.RequestCacheConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

@Configuration
public class FoundationConfiguration {
    @Bean
    @ConditionalOnProperty(name = "mes.runtime.enabled", havingValue = "false", matchIfMissing = true)
    FactoryCatalog factoryCatalog() {
        return new InMemoryFactoryCatalog(List.of());
    }

    @Bean
    @ConditionalOnProperty(name = "mes.system.enabled", havingValue = "false", matchIfMissing = true)
    FactoryPreference preferences() {
        return new InMemoryFactoryPreference();
    }

    @Bean
    @ConditionalOnProperty(name = "mes.system.enabled", havingValue = "false", matchIfMissing = true)
    AuthorizationService authorization() {
        return (actor, scope, permission) -> false;
    }

    @Bean
    @ConditionalOnProperty(name = "mes.system.enabled", havingValue = "false", matchIfMissing = true)
    UserDetailsService users() {
        return name -> {
            throw new UsernameNotFoundException("Account not found");
        };
    }

    @Bean
    FactorySelectionService selection(FactoryCatalog catalog, FactoryPreference preference) {
        return new FactorySelectionService(catalog, preference);
    }

    @Bean
    @ConditionalOnProperty(name = "mes.runtime.enabled", havingValue = "false", matchIfMissing = true)
    ConfiguredBusinessPersistenceResolver persistenceResolver(FactoryCatalog catalog) {
        return new ConfiguredBusinessPersistenceResolver(catalog, Map.of());
    }

    @Bean
    @ConditionalOnProperty(name = "mes.system.enabled", havingValue = "false", matchIfMissing = true)
    BusinessPersistence businessPersistence(BusinessPersistenceResolver resolver, RuntimeRegistry registry) {
        return new DedicatedDatabasePersistence(resolver, registry);
    }

    @Bean
    @ConditionalOnProperty(name = "mes.runtime.enabled", havingValue = "false", matchIfMissing = true)
    RuntimeRegistry runtimes() {
        return new RuntimeRegistry();
    }


    @Bean
    PublicEndpointMatcher publicEndpoints(
            @Qualifier("requestMappingHandlerMapping") ObjectProvider<RequestMappingHandlerMapping> mappings) {
        return new PublicEndpointMatcher(mappings);
    }

    @Bean
    SecurityFilterChain security(HttpSecurity http, UserDetailsService users,
                                 ObjectProvider<SystemTokenFilter> systemTokens, Environment env,
                                 PublicEndpointMatcher publicEndpoints) throws Exception {
        var provider = new DaoAuthenticationProvider(users);
        provider.setPasswordEncoder(PasswordEncoderFactories.createDelegatingPasswordEncoder());
        http.authenticationProvider(provider);
        if (systemTokens.getIfAvailable() != null)
            http.addFilterBefore(systemTokens.getObject(), BasicAuthenticationFilter.class);
        if (env.getProperty("mes.system.enabled", Boolean.class, false))
            http.httpBasic(AbstractHttpConfigurer::disable);
        else http.httpBasic(basic -> basic.authenticationEntryPoint((request, response, error) -> {
            response.setHeader("WWW-Authenticate", "Basic realm=mes-base");
            ErrorWriter.write(ErrorCode.UNAUTHENTICATED, request, response);
        }));
        return http.csrf(AbstractHttpConfigurer::disable).requestCache(RequestCacheConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint((request, response, failure) -> ErrorWriter.write(ErrorCode.UNAUTHENTICATED, request, response))
                        .accessDeniedHandler((request, response, failure) -> ErrorWriter.write(ErrorCode.FORBIDDEN, request, response)))
                .authorizeHttpRequests(requests -> requests
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .requestMatchers(publicEndpoints).permitAll()
                        .anyRequest().authenticated())
                .formLogin(AbstractHttpConfigurer::disable).logout(AbstractHttpConfigurer::disable).build();
    }

    private static Consumer<FactoryScope> admission(ManagedDatasourceService managed) {
        if (managed == null) return null;
        return managed::admit;
    }

    @Bean
    WebMvcConfigurer accessRules(FactorySelectionService selection, AuthorizationService authorization,
                                 ObjectProvider<PlatformAuthorization> platform,
                                 ObjectProvider<ManagedDatasourceService> managed) {
        return new WebMvcConfigurer() {
            public void addInterceptors(InterceptorRegistry registry) {
                registry.addInterceptor(new FoundationAccessInterceptor(selection, authorization, platform.getIfAvailable(() -> (actor, permission) -> false),
                        admission(managed.getIfAvailable())));
            }
        };
    }

}
