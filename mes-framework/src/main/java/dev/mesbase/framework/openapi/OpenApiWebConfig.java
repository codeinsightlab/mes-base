package dev.mesbase.framework.openapi;

import dev.mesbase.system.openapi.service.OpenApiClientService;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.*;
import org.springframework.core.Ordered;
import org.springframework.web.servlet.config.annotation.*;

@Configuration
@ConditionalOnProperty(name={"mes.system.enabled","open-api.enabled"},havingValue="true")
public class OpenApiWebConfig implements WebMvcConfigurer {
    private final OpenApiClientService clients;
    private final OpenApiRateLimiter limiter;
    private final OpenApiDataScopeVerification verification;
    public OpenApiWebConfig(OpenApiClientService clients, OpenApiRateLimiter limiter,
            OpenApiDataScopeVerification verification) {
        this.clients=clients; this.limiter=limiter; this.verification=verification;
    }
    @Bean static OpenApiDataScopeVerification dataScopeVerification(OpenApiProperties properties,
            List<OpenApiDataScopeValidator> validators) {
        return new OpenApiDataScopeVerification(properties,validators);
    }
    @Override public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new OpenApiAuthorizationInterceptor(clients,limiter,verification))
                .addPathPatterns("/open-api","/open-api/**").order(Ordered.HIGHEST_PRECEDENCE);
    }
}
