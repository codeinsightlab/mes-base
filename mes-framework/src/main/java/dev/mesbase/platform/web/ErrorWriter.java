package dev.mesbase.platform.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import dev.mesbase.core.error.ErrorCode;
import dev.mesbase.core.error.FoundationException;

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
        return body(code, code.safeMessage(), request);
    }

    public static ErrorResponse body(FoundationException failure, HttpServletRequest request) {
        ErrorCode code = responseCode(failure.code(), request);
        return body(code, failure.safeMessage(), request);
    }

    private static ErrorResponse body(ErrorCode code, String message, HttpServletRequest request) {
        return new ErrorResponse(code.name(), message, requestId(request));
    }

    public static void write(ErrorCode code, HttpServletRequest request,
                             HttpServletResponse response) throws IOException {
        writeResponse(body(code, request), request, response);
    }

    public static void write(FoundationException failure, HttpServletRequest request,
                             HttpServletResponse response) throws IOException {
        writeResponse(body(failure, request), request, response);
    }

    private static void writeResponse(ErrorResponse body, HttpServletRequest request,
                                      HttpServletResponse response) throws IOException {
        ErrorCode code = ErrorCode.valueOf(body.errorCode());
        response.setStatus(code.httpStatus());
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("X-Request-Id", body.requestId());
        response.getWriter().write("{\"errorCode\":\"" + body.errorCode() + "\",\"message\":\""
                + json(body.message()) + "\",\"requestId\":\"" + body.requestId() + "\"}");
    }

    private static String json(String value) { return value.replace("\\", "\\\\").replace("\"", "\\\""); }
}
