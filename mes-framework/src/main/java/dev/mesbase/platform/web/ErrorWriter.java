package dev.mesbase.platform.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import dev.mesbase.core.error.ErrorCode;

public final class ErrorWriter {
    private ErrorWriter() {
    }

    public static String requestId(HttpServletRequest request) {
        return RequestBoundaryFilter.requestId(request);
    }

    /**
     * Only factory-context failures offer factory selection as HTTP recovery.
     */
    public static ErrorCode responseCode(ErrorCode code, HttpServletRequest request) {
        if (EndpointScopePolicy.scope(request.getRequestURI()) == EndpointAccess.PermissionScope.FACTORY
                && (code == ErrorCode.FACTORY_DISABLED || code == ErrorCode.FACTORY_NOT_FOUND
                || code == ErrorCode.FACTORY_SCOPE_REQUIRED || code == ErrorCode.RUNTIME_NOT_CONFIGURED)) {
            return ErrorCode.FACTORY_SELECTION_REQUIRED;
        }
        return code;
    }

    public static ErrorResponse body(ErrorCode code, HttpServletRequest request) {
        code = responseCode(code, request);
        return new ErrorResponse(code.name(), code.safeMessage(), requestId(request));
    }

    public static void write(ErrorCode code, HttpServletRequest request,
                             HttpServletResponse response) throws IOException {
        code = responseCode(code, request);
        response.setStatus(code.httpStatus());
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("X-Request-Id", requestId(request));
        // Only server generated UUID and constant enum values enter this serialization.
        response.getWriter()
                .write("{\"errorCode\":\"" + code.name() + "\",\"message\":\"" + code.safeMessage() + "\",\"requestId\":\"" + requestId(request) + "\"}");
    }
}
