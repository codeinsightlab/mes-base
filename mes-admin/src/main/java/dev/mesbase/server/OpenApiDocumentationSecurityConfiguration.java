package dev.mesbase.server;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Only exact GET documentation resources are public; API invocation remains independently authenticated.
 */
@Configuration
@ConditionalOnProperty(name = "open-api.enabled", havingValue = "true")
public class OpenApiDocumentationSecurityConfiguration {
    @Bean
    @Order(-1)
    SecurityFilterChain openApiDocumentationSecurity(HttpSecurity security) throws Exception {
        return security.securityMatcher("/openapi-docs/**", "/v3/api-docs/open-api")
                .csrf(config -> config.disable()).formLogin(config -> config.disable())
                .httpBasic(config -> config.disable())
                .logout(config -> config.disable()).requestCache(config -> config.disable())
                .sessionManagement(config -> config.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(config -> config.authenticationEntryPoint((request, response, failure) -> response.setStatus(403))
                        .accessDeniedHandler((request, response, failure) -> response.setStatus(403)))
                .authorizeHttpRequests(config -> config.requestMatchers(HttpMethod.GET,
                        "/openapi-docs/", "/openapi-docs/index.html", "/openapi-docs/app.js", "/openapi-docs/style.css",
                        "/openapi-docs/logo.svg", "/v3/api-docs/open-api").permitAll().anyRequest().denyAll())
                .headers(config -> config.contentSecurityPolicy(policy -> policy.policyDirectives(
                        "default-src 'self'; script-src 'self'; style-src 'self'; connect-src 'self'; img-src 'self'; "
                                + "object-src 'none'; base-uri 'none'; frame-ancestors 'none'")))
                .build();
    }
}
