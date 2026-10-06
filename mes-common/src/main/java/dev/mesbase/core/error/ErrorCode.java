package dev.mesbase.core.error;
public enum ErrorCode {
    SYNC_RUNNING(409,"同步正在进行"), SYNC_EXECUTION_LOST(409,"同步执行权已失效"),
    RESOURCE_CONFLICT(409, "Resource conflicts with current state"), BAD_REQUEST(400, "Invalid request"), UNAUTHENTICATED(401, "Authentication required"),
    FORBIDDEN(403, "Permission denied"), NOT_FOUND(404, "Resource not found"),
    CONFLICT(409, "Execution binding changed"), UNPROCESSABLE(422, "Request cannot be processed"),
    FACTORY_SCOPE_REQUIRED(400, "Select a factory explicitly"), FACTORY_NOT_FOUND(404, "Factory not found"),
    FACTORY_DISABLED(422, "Factory is disabled"), RUNTIME_NOT_CONFIGURED(422, "Business runtime is not configured"),
    INTEGRATION_NOT_CONFIGURED(422, "ERP integration is not configured"), EXECUTION_UNKNOWN(503,"Database saved; scheduler result UNKNOWN. Retry synchronization or check task logs"), INTERNAL_ERROR(500, "Internal server error");
    private final int httpStatus; private final String message;
    ErrorCode(int status, String message) { this.httpStatus=status; this.message=message; }
    public int httpStatus() { return httpStatus; }
    public String safeMessage() { return message; }
}
