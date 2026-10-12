package dev.mesbase.framework.openapi;

import dev.mesbase.common.openapi.OpenApiPrincipal;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

public class OpenApiAuthenticationFilter extends OncePerRequestFilter {
    private final OpenApiTokenService tokens;
    private final OpenApiRateLimiter limiter;

    public OpenApiAuthenticationFilter(OpenApiTokenService tokens, OpenApiRateLimiter limiter) {
        this.tokens = tokens;
        this.limiter = limiter;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !OpenApiHttp.isOpenApi(request);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        if (OpenApiHttp.isTokenRequest(request)) {
            // 仅使用直连peer IP。代理后的地址必须由受信入口配置，拒信任任意X-Forwarded-For。
            limiter.tokenRequest(request.getRemoteAddr());
        } else {
            OpenApiPrincipal principal = tokens.authenticate(OpenApiHttp.bearer(request));
            request.setAttribute(OpenApiHttp.IDENTITY, principal);
            SecurityContextHolder.getContext().setAuthentication(new OpenApiAuthentication(principal));
        }
        try {
            chain.doFilter(request, response);
        } finally {
            // 在SecurityContextPersistenceFilter清理之前保留已验证身份，用于最外层访问日志。
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication instanceof OpenApiAuthentication) {
                request.setAttribute(OpenApiHttp.IDENTITY, ((OpenApiAuthentication) authentication).getPrincipal());
            }
        }
    }
}
