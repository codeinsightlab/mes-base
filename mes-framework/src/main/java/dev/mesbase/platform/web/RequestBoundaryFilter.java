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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public final class RequestBoundaryFilter extends OncePerRequestFilter {
    public static final String REQUEST_ID = "mes.requestId";
    private static final Logger LOG = LoggerFactory.getLogger(RequestBoundaryFilter.class);

    @Override
    protected boolean shouldNotFilterAsyncDispatch() {
        return false;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        var previous = FactoryContext.current();
        String previousId = MDC.get("requestId");
        String requestId = requestId(request);
        response.setHeader("X-Request-Id", requestId);
        MDC.put("requestId", requestId);
        try {
            FactoryContext.clear();
            PageHelper.clearPage();
            chain.doFilter(request, response);
        } catch (FoundationException failure) {
            LOG.warn("Foundation request failed requestId={}", requestId, failure);
            if (response.isCommitted()) throw failure;
            ErrorWriter.write(failure.code(), request, response);
        } finally {
            try {
                BusinessRequestSnapshot.finish(request);
                FactoryContext.clear();
                previous.ifPresent(FactoryContext::bind);
            } finally {
                PageHelper.clearPage();
                if (previousId == null) MDC.remove("requestId");
                else MDC.put("requestId", previousId);
            }
        }
    }

    /**
     * Server-owned request attribute persists across dispatches; client headers are not trusted.
     */
    public static String requestId(HttpServletRequest request) {
        Object existing = request.getAttribute(REQUEST_ID);
        if (existing instanceof String id && !id.isBlank()) return id;
        String id = UUID.randomUUID().toString();
        request.setAttribute(REQUEST_ID, id);
        return id;
    }
}
