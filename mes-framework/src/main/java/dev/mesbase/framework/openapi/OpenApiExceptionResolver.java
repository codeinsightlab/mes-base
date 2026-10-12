package dev.mesbase.framework.openapi;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.UncheckedIOException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerExceptionResolver;
import org.springframework.web.servlet.ModelAndView;

/**
 * 仅接管OpenAPI的MVC异常，先于原有全局Advice；Filter异常由外层Filter处理。
 */
@Component
@ConditionalOnProperty(name = {"mes.system.enabled", "open-api.enabled"}, havingValue = "true")
public class OpenApiExceptionResolver implements HandlerExceptionResolver, Ordered {
    private final OpenApiHttp http;

    public OpenApiExceptionResolver(OpenApiHttp http) {
        this.http = http;
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

    @Override
    public ModelAndView resolveException(HttpServletRequest request, HttpServletResponse response, Object handler,
                                         Exception ex) {
        if (!OpenApiHttp.isOpenApi(request)) return null;
        request.setAttribute(OpenApiHttp.FAILURE, ex);
        try {
            http.error(request, response, OpenApiErrors.classify(ex));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return new ModelAndView();
    }
}
