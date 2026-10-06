package dev.mesbase.framework.openapi;

import dev.mesbase.common.openapi.OpenApiPrincipal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import dev.mesbase.platform.web.RequestBoundaryFilter;

/**
 * 在专用Security链的最外层覆盖成功、认证拒绝、限流和异常。
 */
public class OpenApiRequestFilter extends OncePerRequestFilter {
    private static final Logger LOG = LoggerFactory.getLogger("openapi.access");
    private final OpenApiHttp http;

    public OpenApiRequestFilter(OpenApiHttp http) {
        this.http = http;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !OpenApiHttp.isOpenApi(request);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        long started = System.currentTimeMillis();
        String requestId = RequestBoundaryFilter.requestId(request);
        String previous = MDC.get("requestId");
        request.setAttribute(OpenApiHttp.REQUEST_ID, requestId);
        response.setHeader("X-Request-Id", requestId);
        response.setHeader("Cache-Control", "no-store");
        MDC.put("requestId", requestId);
        SecurityContextHolder.clearContext();
        try {
            chain.doFilter(request, response);
        } catch (Exception failure) {
            request.setAttribute(OpenApiHttp.FAILURE, failure);
            request.setAttribute(OpenApiHttp.ERROR, OpenApiErrors.classify(failure).getCode());
            if (response.isCommitted()) {
                if (failure instanceof IOException) throw (IOException) failure;
                throw new ServletException(failure);
            }
            http.error(request, response, OpenApiErrors.classify(failure));
        } finally {
            // 日志仅白名单字段；不打印body/query/headers/异常原始消息或凭证摘要。
            try {
                OpenApiPrincipal identity = (OpenApiPrincipal) request.getAttribute(OpenApiHttp.IDENTITY);
                Throwable failure = (Throwable) request.getAttribute(OpenApiHttp.FAILURE);
                LOG.info("requestId={} clientId={} appKey={} apiCode={} method={} uri={} startedAt={} status={} errorCode={} durationMs={} peerIp={} failureTrace={}",
                        requestId, identity == null ? null : identity.getClientId(), identity == null ? null : identity.getAppKey(),
                        request.getAttribute(OpenApiHttp.API_CODE),
                        safe(request.getMethod()), safe(OpenApiHttp.path(request)), started, response.getStatus(),
                        request.getAttribute(OpenApiHttp.ERROR), System.currentTimeMillis() - started,
                        safe(request.getRemoteAddr()), failureTrace(failure));
            } finally {
                SecurityContextHolder.clearContext();
                if (previous == null) MDC.remove("requestId");
                else MDC.put("requestId", previous);
            }
        }
    }

    /**
     * 保留异常链类型和源码位置以定位问题，排除可能含SQL/凭证的异常message。
     */
    private String failureTrace(Throwable failure) {
        if (failure == null) return "";
        StringBuilder trace = new StringBuilder();
        for (int depth = 0; failure != null && depth < 8; depth++, failure = failure.getCause()) {
            if (depth > 0) trace.append(" <- ");
            trace.append(failure.getClass().getName());
            StackTraceElement[] frames = failure.getStackTrace();
            for (int i = 0; i < Math.min(frames.length, 16); i++) trace.append(" at ").append(frames[i]);
        }
        return trace.toString();
    }

    private String safe(String text) {
        if (text == null) return "";
        return text.replaceAll("[\r\n\t]", "_").substring(0, Math.min(text.length(), 512));
    }
}
