package dev.mesbase.framework.openapi;

import dev.mesbase.common.openapi.OpenApiDataScopeIgnore;
import dev.mesbase.common.openapi.OpenApiPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.web.method.HandlerMethod;

/**
 * Switch affects business data validation only; authentication/API grants remain mandatory.
 */
public final class OpenApiDataScopeVerification {
    private final boolean enabled;
    private final OpenApiDataScopeValidator validator;

    public OpenApiDataScopeVerification(OpenApiProperties properties, List<OpenApiDataScopeValidator> validators) {
        enabled = properties.getDataScope().isEnabled();
        if (enabled && validators.size() != 1)
            throw new IllegalStateException("open-api.data-scope.enabled requires exactly one MES OpenApiDataScopeValidator");
        validator = enabled ? validators.getFirst() : null;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void verify(OpenApiPrincipal principal, String apiCode, HttpServletRequest request, HandlerMethod endpoint) {
        if (enabled && !AnnotatedElementUtils.hasAnnotation(endpoint.getMethod(), OpenApiDataScopeIgnore.class))
            validator.validate(principal, apiCode, request, endpoint);
    }
}
