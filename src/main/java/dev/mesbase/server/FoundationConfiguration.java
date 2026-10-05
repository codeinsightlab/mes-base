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
    @Bean @Profile("!demo") FactoryCatalog factoryCatalog() { return new InMemoryFactoryCatalog(List.of()); }
    @Bean FactoryPreference preferences() { return new InMemoryFactoryPreference(); }
    @Bean @Profile("!demo") AuthorizationService authorization() { return (actor,scope,permission) -> false; }
    @Bean @Profile("!demo") UserDetailsService users() { return name -> { throw new UsernameNotFoundException("Account not found"); }; }
    @Bean FactorySelectionService selection(FactoryCatalog catalog,FactoryPreference preference) { return new FactorySelectionService(catalog,preference); }
    @Bean ConfiguredBusinessPersistenceResolver persistenceResolver(FactoryCatalog catalog) { return new ConfiguredBusinessPersistenceResolver(catalog,java.util.Map.of()); }
    @Bean BusinessPersistence businessPersistence(ConfiguredBusinessPersistenceResolver resolver,RuntimeRegistry registry) { return new DedicatedDatabasePersistence(resolver,registry); }
    @Bean RuntimeRegistry runtimes() { return new RuntimeRegistry(); }
    @Bean FactoryErpConnectionResolver erp() { return new NotConfiguredErpResolver(); }
    @Bean SecurityFilterChain security(HttpSecurity http,UserDetailsService users) throws Exception {
        var provider=new org.springframework.security.authentication.dao.DaoAuthenticationProvider(users);
        provider.setPasswordEncoder(org.springframework.security.crypto.factory.PasswordEncoderFactories.createDelegatingPasswordEncoder());
        http.authenticationProvider(provider);
        return http.csrf(csrf -> csrf.disable()).requestCache(cache -> cache.disable())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(requests -> requests.anyRequest().permitAll())
            .httpBasic(basic -> basic.authenticationEntryPoint((request,response,error) -> { response.setHeader("WWW-Authenticate","Basic realm=mes-base"); ErrorWriter.write(ErrorCode.UNAUTHENTICATED,request,response); }))
            .formLogin(form -> form.disable()).logout(logout -> logout.disable()).build();
    }
    @Bean WebMvcConfigurer accessRules(FactorySelectionService selection,AuthorizationService authorization) {
        return new WebMvcConfigurer() { public void addInterceptors(InterceptorRegistry registry) { registry.addInterceptor(new FoundationAccessInterceptor(selection,authorization)); } };
    }
    @Bean SmartInitializingSingleton explicitEndpoints(@Qualifier("requestMappingHandlerMapping") RequestMappingHandlerMapping mappings) {
        return () -> mappings.getHandlerMethods().values().forEach(EndpointCatalog::required);
    }
}
