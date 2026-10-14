package dev.mesbase.platform.web;

import dev.mesbase.framework.openapi.OpenApiHttp;
import dev.mesbase.framework.openapi.OpenApiRequestFilter;
import dev.mesbase.platform.execution.FactoryContext;
import jakarta.servlet.ServletRequestEvent;
import jakarta.servlet.ServletRequestListener;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.util.AntPathMatcher;

/**
 * Container lifecycle, including async completion and final error dispatch, owns completion.
 */
public final class HttpRequestCompletionListener implements ServletRequestListener {
    private static final Logger ACCESS = LoggerFactory.getLogger("MES_HTTP_ACCESS");
    private static final Logger LOG = LoggerFactory.getLogger(HttpRequestCompletionListener.class);
    private static final String STATE = HttpRequestCompletionListener.class.getName();
    private final List<String> excludedPaths;
    private final long slowThresholdMs;
    private final AntPathMatcher paths = new AntPathMatcher();

    public HttpRequestCompletionListener(List<String> excludedPaths, long slowThresholdMs) {
        this.excludedPaths = List.copyOf(excludedPaths);
        this.slowThresholdMs = slowThresholdMs;
    }

    private static final class State {
        final long started = System.nanoTime();
        final String method;
        final String path;
        final boolean openApi;
        final AtomicBoolean completed = new AtomicBoolean();
        volatile HttpServletResponse response;
        volatile String factoryId;
        volatile String userId;

        State(HttpServletRequest request, HttpServletResponse response) {
            method = safe(request.getMethod());
            path = safe(request.getRequestURI());
            openApi = OpenApiHttp.isOpenApi(request);
            this.response = response;
        }
    }

    static void begin(HttpServletRequest request, HttpServletResponse response) {
        synchronized (request) {
            if (request.getAttribute(STATE) instanceof State state) state.response = response;
            else request.setAttribute(STATE, new State(request, response));
        }
    }

    static void captureFactory(HttpServletRequest request) {
        if (request.getAttribute(STATE) instanceof State state) {
            FactoryContext.current().ifPresent(scope -> state.factoryId = safe(scope.factoryId().value()));
        }
    }

    public static void captureUser(HttpServletRequest request, Long userId) {
        if (request.getAttribute(STATE) instanceof State state && userId != null) {
            state.userId = userId.toString();
        }
    }

    @Override
    public void requestDestroyed(ServletRequestEvent event) {
        if (!(event.getServletRequest() instanceof HttpServletRequest request)
                || !(request.getAttribute(STATE) instanceof State state)
                || !state.completed.compareAndSet(false, true)) return;
        String path = state.path.substring(Math.min(request.getContextPath().length(), state.path.length()));
        if (excludedPaths.stream().anyMatch(pattern -> paths.match(pattern, path))) return;
        Map<String, String> previous = MDC.getCopyOfContextMap();
        String id = RequestBoundaryFilter.requestId(request);
        MDC.put("requestId", id);
        try {
            long duration = Math.max(0, (System.nanoTime() - state.started) / 1_000_000);
            if (state.openApi) {
                OpenApiRequestFilter.logCompletion(request, state.response, state.method, state.path, duration);
            } else {
                int status = state.response.getStatus();
                ACCESS.info("event=http.request.completed method={} path={} status={} durationMs={} factoryId={} userId={}",
                        state.method, state.path, status, duration, state.factoryId, state.userId);
            }
            if (duration >= slowThresholdMs) {
                LOG.warn("event=http.request.slow path={} durationMs={}", state.path, duration);
            }
        } finally {
            RequestBoundaryFilter.restoreMdc(previous);
        }
    }

    public static String safe(String value) {
        if (value == null) return "";
        String bounded = value.substring(0, Math.min(value.length(), 512));
        return bounded.replaceAll("[\\p{Cntrl}\\s=]", "_");
    }
}
