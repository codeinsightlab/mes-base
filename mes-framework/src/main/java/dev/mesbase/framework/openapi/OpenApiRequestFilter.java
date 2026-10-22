package dev.mesbase.framework.openapi;

import java.util.Optional;

import dev.mesbase.common.openapi.OpenApiPrincipal;
import dev.mesbase.platform.web.RequestBoundaryFilter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import dev.mesbase.platform.web.HttpRequestCompletionListener;
import dev.mesbase.platform.web.HttpFailureLogging;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 在专用Security链的最外层覆盖成功、认证拒绝、限流和异常。
 */
public class OpenApiRequestFilter extends OncePerRequestFilter {
    private static final Logger LOG = LoggerFactory.getLogger("MES_HTTP_ACCESS");
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
        String requestId = RequestBoundaryFilter.requestId(request);
        request.setAttribute(OpenApiHttp.REQUEST_ID, requestId);
        response.setHeader("X-Request-Id", requestId);
        response.setHeader("Cache-Control", "no-store");
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
            SecurityContextHolder.clearContext();
        }
    }

    /**
     * One terminal event, invoked by the shared container completion listener.
     */
    public static void logCompletion(HttpServletRequest request, HttpServletResponse response,
                                     String method, String path, long durationMs) {
        OpenApiPrincipal identity = (OpenApiPrincipal) request.getAttribute(OpenApiHttp.IDENTITY);
        Throwable failure = (Throwable) request.getAttribute(OpenApiHttp.FAILURE);
        if (failure != null && OpenApiErrors.classify(failure).getStatus() >= 500) {
            // Preserve full source/cause stacks without exposing credential-bearing messages.
            HttpFailureLogging.openApi(request, failure, OpenApiErrors.classify(failure).getCode());
        }
        LOG.info("event=http.request.completed method={} path={} status={} durationMs={} factoryId={} userId={} clientId={} appKey={} apiCode={} errorCode={} peerIp={}",
                method, path, response.getStatus(), durationMs, null, null,
                Optional.ofNullable(identity).map(nullableValue -> nullableValue.getClientId()).orElse(null), Optional.ofNullable(identity).map(nullableValue -> safe(nullableValue.getAppKey())).orElse(null),
                safe(String.valueOf(request.getAttribute(OpenApiHttp.API_CODE))),
                request.getAttribute(OpenApiHttp.ERROR), safe(request.getRemoteAddr()));
    }

    private static String safe(String text) {
        return HttpRequestCompletionListener.safe(text);
    }
}
