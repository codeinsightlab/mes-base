package dev.mesbase.platform.web;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.core.annotation.Order;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.slf4j.*;
import dev.mesbase.platform.execution.FactoryContext;
import dev.mesbase.core.error.*;
@Component @Order(Ordered.HIGHEST_PRECEDENCE+20)
public final class RequestBoundaryFilter extends OncePerRequestFilter {
    private static final Logger LOG=LoggerFactory.getLogger(RequestBoundaryFilter.class);
    protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain) throws ServletException,IOException {
        var previous=FactoryContext.current(); String previousId=MDC.get("requestId");
        String requestId=java.util.UUID.randomUUID().toString();request.setAttribute("mes.requestId",requestId);response.setHeader("X-Request-Id",requestId);MDC.put("requestId",requestId);
        try { FactoryContext.clear(); chain.doFilter(request,response); }
        catch(FoundationException failure) { LOG.warn("Foundation request failed requestId={}",requestId,failure); if(response.isCommitted()) throw failure; ErrorWriter.write(failure.code(),request,response); }
        finally { FactoryContext.clear(); previous.ifPresent(FactoryContext::bind); if(previousId==null) MDC.remove("requestId"); else MDC.put("requestId",previousId); }
    }
}
