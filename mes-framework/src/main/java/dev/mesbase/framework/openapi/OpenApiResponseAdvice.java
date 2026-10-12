package dev.mesbase.framework.openapi;

import dev.mesbase.common.core.domain.AjaxResult;
import dev.mesbase.common.openapi.OpenApiError;
import dev.mesbase.common.openapi.OpenApiException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

@ControllerAdvice
@ConditionalOnProperty(name = {"mes.system.enabled", "open-api.enabled"}, havingValue = "true")
public class OpenApiResponseAdvice implements ResponseBodyAdvice<Object> {
    private final OpenApiHttp http;

    public OpenApiResponseAdvice(OpenApiHttp http) {
        this.http = http;
    }

    @Override
    public boolean supports(MethodParameter parameter, Class<? extends HttpMessageConverter<?>> converter) {
        return true;
    }

    @Override
    public Object beforeBodyWrite(Object body, MethodParameter parameter, MediaType type,
                                  Class<? extends HttpMessageConverter<?>> converter, ServerHttpRequest request,
                                  ServerHttpResponse response) {
        if (!(request instanceof ServletServerHttpRequest)) return body;
        HttpServletRequest servlet = ((ServletServerHttpRequest) request).getServletRequest();
        if (!OpenApiHttp.isOpenApi(servlet)) return body;
        if (!(body instanceof AjaxResult)) throw new OpenApiException(OpenApiError.INTERNAL_ERROR);
        AjaxResult result = (AjaxResult) body;
        // 业务异常必须抛出，由局部resolver统一映射，不能隐藏在HTTP 200中。
        if (!Integer.valueOf(200).equals(result.get("code"))) throw new OpenApiException(OpenApiError.INTERNAL_ERROR);
        return http.decorate(result, servlet);
    }
}
