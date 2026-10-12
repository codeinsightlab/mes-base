package dev.mesbase.framework.openapi;

import dev.mesbase.common.openapi.OpenApiEndpoint;
import dev.mesbase.common.openapi.OpenApiError;
import dev.mesbase.common.openapi.OpenApiException;
import dev.mesbase.common.openapi.OpenApiPrincipal;
import dev.mesbase.system.openapi.dto.OpenApiAuthorizationQuery;
import dev.mesbase.system.openapi.service.OpenApiClientService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

public class OpenApiAuthorizationInterceptor implements HandlerInterceptor {
    private final OpenApiClientService clients;
    private final OpenApiRateLimiter limiter;
    private final OpenApiDataScopeVerification verification;

    public OpenApiAuthorizationInterceptor(OpenApiClientService clients, OpenApiRateLimiter limiter,
                                           OpenApiDataScopeVerification verification) {
        this.clients = clients;
        this.limiter = limiter;
        this.verification = verification;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (OpenApiHttp.isTokenRequest(request)) return true;
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof OpenApiAuthentication))
            throw new OpenApiException(OpenApiError.INVALID_CREDENTIALS);
        OpenApiPrincipal principal = ((OpenApiAuthentication) authentication).getPrincipal();
        if (OpenApiHttp.isRevokeRequest(request)) {
            request.setAttribute(OpenApiHttp.API_CODE, "token.revoke");
            limiter.api(new OpenApiInvocation(principal, "token.revoke"));
            return true;
        }
        if (!OpenApiHttp.path(request).startsWith("/open-api/v1/") || !(handler instanceof HandlerMethod))
            throw new OpenApiException(OpenApiError.ACCESS_DENIED);
        OpenApiEndpoint endpoint = AnnotatedElementUtils.findMergedAnnotation(((HandlerMethod) handler).getMethod(), OpenApiEndpoint.class);
        if (endpoint == null || !endpoint.value().matches("[a-z][a-z0-9.:_-]{0,99}"))
            throw new OpenApiException(OpenApiError.ACCESS_DENIED);
        request.setAttribute(OpenApiHttp.API_CODE, endpoint.value());
        clients.authorize(new OpenApiAuthorizationQuery(principal, endpoint.value()));
        limiter.api(new OpenApiInvocation(principal, endpoint.value()));
        verification.verify(principal, endpoint.value(), request, (HandlerMethod) handler);
        return true;
    }
}
