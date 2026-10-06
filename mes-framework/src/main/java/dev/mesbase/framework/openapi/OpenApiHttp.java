package dev.mesbase.framework.openapi;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.mesbase.common.core.domain.AjaxResult;
import dev.mesbase.common.openapi.*;
import org.springframework.stereotype.Component;
import jakarta.servlet.http.*;
import java.io.IOException;

@Component
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name={"mes.system.enabled","open-api.enabled"},havingValue="true")
public class OpenApiHttp {
    static final String REQUEST_ID = OpenApiHttp.class.getName() + ".requestId";
    static final String IDENTITY = OpenApiHttp.class.getName() + ".identity";
    static final String ERROR = OpenApiHttp.class.getName() + ".error";
    static final String FAILURE = OpenApiHttp.class.getName() + ".failure";
    static final String API_CODE = OpenApiHttp.class.getName() + ".apiCode";
    private final ObjectMapper json;
    public OpenApiHttp(@org.springframework.beans.factory.annotation.Qualifier("openApiJson") ObjectMapper json) { this.json = json; }
    static String path(HttpServletRequest request) { return request.getRequestURI().substring(request.getContextPath().length()); }
    public static boolean isOpenApi(HttpServletRequest request) {
        String path = path(request); return path.equals("/open-api") || path.startsWith("/open-api/");
    }
    static boolean isTokenRequest(HttpServletRequest request) {
        return "POST".equals(request.getMethod()) && "/open-api/token".equals(path(request));
    }
    static boolean isRevokeRequest(HttpServletRequest request) {
        return "POST".equals(request.getMethod()) && "/open-api/token/revoke".equals(path(request));
    }
    static String bearer(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) throw new OpenApiException(OpenApiError.INVALID_CREDENTIALS);
        return header.substring(7);
    }
    AjaxResult decorate(AjaxResult result, HttpServletRequest request) {
        result.put("requestId", request.getAttribute(REQUEST_ID));
        if (!result.containsKey("data")) result.put("data", null);
        return result;
    }
    public void error(HttpServletRequest request, HttpServletResponse response, OpenApiError error) throws IOException {
        request.setAttribute(ERROR, error.getCode());
        response.setStatus(error.getStatus());
        response.setContentType("application/json"); response.setCharacterEncoding("UTF-8");
        response.setHeader("Cache-Control", "no-store");
        if (error == OpenApiError.INVALID_CREDENTIALS) response.setHeader("WWW-Authenticate", "Bearer");
        AjaxResult result = new AjaxResult(error.getStatus(), error.getName()).put("errorCode", error.getCode());
        json.writeValue(response.getWriter(), decorate(result, request));
    }
}
