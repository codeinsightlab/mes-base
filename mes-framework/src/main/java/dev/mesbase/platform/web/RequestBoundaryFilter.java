package dev.mesbase.platform.web;

import com.github.pagehelper.PageHelper;
import dev.mesbase.core.error.FoundationException;
import dev.mesbase.platform.execution.FactoryContext;
import dev.mesbase.infrastructure.persistence.BusinessRequestSnapshot;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import java.util.Map;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public final class RequestBoundaryFilter extends OncePerRequestFilter {
    public static final String REQUEST_ID = "mes.requestId";

    @Override
    protected boolean shouldNotFilterAsyncDispatch() {
        return false;
    }

    @Override
    protected boolean shouldNotFilterErrorDispatch() {
        return false;
    }

    @Override
    protected void doFilterNestedErrorDispatch(HttpServletRequest request, HttpServletResponse response,
                                               FilterChain chain)
            throws ServletException, IOException {
        Map<String, String> previous = MDC.getCopyOfContextMap();
        MDC.put("requestId", requestId(request));
        try {
            chain.doFilter(request, response);
        } finally {
            restoreMdc(previous);
        }
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        var previous = FactoryContext.current();
        Map<String, String> previousMdc = MDC.getCopyOfContextMap();
        String requestId = requestId(request);
        HttpRequestCompletionListener.begin(request, response);
        response.setHeader("X-Request-Id", requestId);
        MDC.put("requestId", requestId);
        try {
            FactoryContext.clear();
            PageHelper.clearPage();
            chain.doFilter(request, response);
        } catch (FoundationException failure) {
            HttpFailureLogging.known(request, failure);
            if (response.isCommitted()) throw failure;
            ErrorWriter.write(failure.code(), request, response);
        } catch (IOException | ServletException | RuntimeException failure) {
            HttpFailureLogging.unexpected(request, failure);
            throw failure;
        } finally {
            HttpRequestCompletionListener.captureFactory(request);
            try {
                BusinessRequestSnapshot.finish(request);
                FactoryContext.clear();
                previous.ifPresent(FactoryContext::bind);
            } finally {
                PageHelper.clearPage();
                restoreMdc(previousMdc);
            }
        }
    }

    static void restoreMdc(Map<String, String> previous) {
        if (previous == null) MDC.clear();
        else MDC.setContextMap(previous);
    }

    /**
     * Server-owned request attribute persists across dispatches; client headers are not trusted.
     */
    public static String requestId(HttpServletRequest request) {
        Object existing = request.getAttribute(REQUEST_ID);
        if (existing instanceof String id && id.matches("[A-Za-z0-9_-][A-Za-z0-9._-]{0,127}")) return id;
        String id = UUID.randomUUID().toString();
        request.setAttribute(REQUEST_ID, id);
        return id;
    }
}
