package dev.mesbase.framework.openapi;

import dev.mesbase.common.openapi.OpenApiPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.method.HandlerMethod;

/**
 * Optional MES policy boundary. A policy may inspect operation/resource arguments and resolve
 * an explicit FactoryScope; no route, product or internal-user identity is assumed by Base.
 * Reject using OpenApiException; never silently substitute a default factory or datasource.
 */
public interface OpenApiDataScopeValidator {
    void validate(OpenApiPrincipal principal, String apiCode,
            HttpServletRequest request, HandlerMethod endpoint);
}
