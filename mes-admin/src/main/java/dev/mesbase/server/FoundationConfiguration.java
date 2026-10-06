package dev.mesbase.server;

import org.springframework.context.annotation.*;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.servlet.config.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.*;
import dev.mesbase.core.factory.*;
import dev.mesbase.core.security.*;
import dev.mesbase.infrastructure.catalog.*;
import dev.mesbase.infrastructure.persistence.*;
import dev.mesbase.integration.erp.*;
import dev.mesbase.platform.web.*;
import dev.mesbase.core.error.ErrorCode;

import java.util.List;

@Configuration
public class FoundationConfiguration {
    @Bean
    @org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name = "mes.runtime.enabled", havingValue = "false", matchIfMissing = true)
    FactoryCatalog factoryCatalog() {
        return new InMemoryFactoryCatalog(List.of());
    }

    @Bean
    FactoryPreference preferences() {
        return new InMemoryFactoryPreference();
    }

    @Bean
    @org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name = "mes.system.enabled", havingValue = "false", matchIfMissing = true)
    AuthorizationService authorization() {
        return (actor, scope, permission) -> false;
    }

    @Bean
    @org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name = "mes.system.enabled", havingValue = "false", matchIfMissing = true)
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
    @org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name = "mes.runtime.enabled", havingValue = "false", matchIfMissing = true)
    ConfiguredBusinessPersistenceResolver persistenceResolver(FactoryCatalog catalog) {
        return new ConfiguredBusinessPersistenceResolver(catalog, java.util.Map.of());
    }

    @Bean
    BusinessPersistence businessPersistence(BusinessPersistenceResolver resolver, RuntimeRegistry registry) {
        return new DedicatedDatabasePersistence(resolver, registry);
    }

    @Bean
    @org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name = "mes.runtime.enabled", havingValue = "false", matchIfMissing = true)
    RuntimeRegistry runtimes() {
        return new RuntimeRegistry();
    }

    @Bean
    FactoryErpConnectionResolver erp() {
        return new NotConfiguredErpResolver();
    }

    @Bean
    SecurityFilterChain security(HttpSecurity http, UserDetailsService users, org.springframework.beans.factory.ObjectProvider<dev.mesbase.framework.security.SystemTokenFilter> systemTokens, org.springframework.core.env.Environment env) throws Exception {
        var provider = new org.springframework.security.authentication.dao.DaoAuthenticationProvider(users);
        provider.setPasswordEncoder(org.springframework.security.crypto.factory.PasswordEncoderFactories.createDelegatingPasswordEncoder());
        http.authenticationProvider(provider);
        if (systemTokens.getIfAvailable() != null)
            http.addFilterBefore(systemTokens.getObject(), org.springframework.security.web.authentication.www.BasicAuthenticationFilter.class);
        if (env.getProperty("mes.system.enabled", Boolean.class, false)) http.httpBasic(basic -> basic.disable());
        else http.httpBasic(basic -> basic.authenticationEntryPoint((request, response, error) -> {
            response.setHeader("WWW-Authenticate", "Basic realm=mes-base");
            ErrorWriter.write(ErrorCode.UNAUTHENTICATED, request, response);
        }));
        return http.csrf(csrf -> csrf.disable()).requestCache(cache -> cache.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(requests -> requests.anyRequest().permitAll())
                .formLogin(form -> form.disable()).logout(logout -> logout.disable()).build();
    }

    @Bean
    WebMvcConfigurer accessRules(FactorySelectionService selection, AuthorizationService authorization, org.springframework.beans.factory.ObjectProvider<dev.mesbase.platform.security.PlatformAuthorization> platform) {
        return new WebMvcConfigurer() {
            public void addInterceptors(InterceptorRegistry registry) {
                registry.addInterceptor(new FoundationAccessInterceptor(selection, authorization, platform.getIfAvailable(() -> (actor, permission) -> false)));
            }
        };
    }

    @Bean
    SmartInitializingSingleton explicitEndpoints(@Qualifier("requestMappingHandlerMapping") RequestMappingHandlerMapping mappings) {
        return () -> mappings.getHandlerMethods().values().forEach(EndpointCatalog::required);
    }
}
