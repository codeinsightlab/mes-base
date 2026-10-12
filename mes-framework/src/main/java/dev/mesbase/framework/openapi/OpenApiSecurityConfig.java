package dev.mesbase.framework.openapi;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.mesbase.common.openapi.OpenApiError;
import java.time.Clock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.context.SecurityContextHolderFilter;

/**
 * SOURCE_PRODUCT_PORT: Source dedicated chain adapted to Security7; no internal-user authorities.
 */
@Configuration
@ConditionalOnProperty(name = {"mes.system.enabled", "open-api.enabled"}, havingValue = "true")
@EnableConfigurationProperties(OpenApiProperties.class)
public class OpenApiSecurityConfig {
    @Bean
    public static Clock openApiClock() {
        return Clock.systemUTC();
    }

    @Bean("openApiJson")
    public static ObjectMapper openApiJson() {
        return new ObjectMapper();
    }

    @Bean
    @Order(0)
    SecurityFilterChain openApiSecurity(HttpSecurity security, OpenApiTokenService tokens,
                                        OpenApiRateLimiter limiter, OpenApiHttp http) throws Exception {
        security.securityMatcher("/open-api", "/open-api/**")
                .csrf(value -> value.disable()).formLogin(value -> value.disable()).httpBasic(value -> value.disable())
                .logout(value -> value.disable()).requestCache(value -> value.disable())
                .sessionManagement(value -> value.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(value -> value
                        .authenticationEntryPoint((request, response, failure) -> http.error(request, response, OpenApiError.INVALID_CREDENTIALS))
                        .accessDeniedHandler((request, response, failure) -> http.error(request, response, OpenApiError.ACCESS_DENIED)))
                .authorizeHttpRequests(value -> value.requestMatchers(HttpMethod.POST, "/open-api/token").permitAll()
                        .anyRequest().authenticated());
        security.addFilterBefore(new OpenApiRequestFilter(http), SecurityContextHolderFilter.class);
        security.addFilterBefore(new OpenApiAuthenticationFilter(tokens, limiter), UsernamePasswordAuthenticationFilter.class);
        return security.build();
    }
}
