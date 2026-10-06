package dev.mesbase.framework.openapi;

import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;

/** 仅接管OpenAPI的MVC异常，先于原有全局Advice；Filter异常由外层Filter处理。 */
@Component
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name={"mes.system.enabled","open-api.enabled"},havingValue="true")
public class OpenApiExceptionResolver implements HandlerExceptionResolver, Ordered {
    private final OpenApiHttp http;
    public OpenApiExceptionResolver(OpenApiHttp http) { this.http = http; }
    @Override public int getOrder() { return Ordered.HIGHEST_PRECEDENCE; }
    @Override public ModelAndView resolveException(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        if (!OpenApiHttp.isOpenApi(request)) return null;
        request.setAttribute(OpenApiHttp.FAILURE, ex);
        try { http.error(request, response, OpenApiErrors.classify(ex)); }
        catch (IOException e) { throw new java.io.UncheckedIOException(e); }
        return new ModelAndView();
    }
}
