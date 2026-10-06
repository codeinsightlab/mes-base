package dev.mesbase.platform.web;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import dev.mesbase.core.error.ErrorCode;
public final class ErrorWriter {
    private ErrorWriter() {}
    public static String requestId(HttpServletRequest request) {
        return RequestBoundaryFilter.requestId(request);
    }
    public static ErrorResponse body(ErrorCode code,HttpServletRequest request) { return new ErrorResponse(code.name(),code.safeMessage(),requestId(request)); }
    public static void write(ErrorCode code,HttpServletRequest request,HttpServletResponse response) throws IOException {
        response.setStatus(code.httpStatus()); response.setContentType("application/json");response.setCharacterEncoding("UTF-8");
        response.setHeader("X-Request-Id",requestId(request));
        // Only server generated UUID and constant enum values enter this serialization.
        response.getWriter().write("{\"errorCode\":\""+code.name()+"\",\"message\":\""+code.safeMessage()+"\",\"requestId\":\""+requestId(request)+"\"}");
    }
}
