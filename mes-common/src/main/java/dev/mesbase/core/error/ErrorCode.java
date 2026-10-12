package dev.mesbase.core.error;

public enum ErrorCode {
    SYNC_QUERY_NOT_IMPLEMENTED(422, "暂未接入外部同步查询接口"),
    ERP_SYNC_NOT_CONFIGURED(422, "暂未接入 ERP 物料查询接口"),
    SYNC_RUNNING(409, "同步正在进行"), SYNC_EXECUTION_LOST(409, "同步执行权已失效"),
    RESOURCE_CONFLICT(409, "Resource conflicts with current state"), BAD_REQUEST(400, "Invalid request"), UNAUTHENTICATED(401, "Authentication required"),
    FORBIDDEN(403, "Permission denied"), NOT_FOUND(404, "Resource not found"),
    CONFLICT(409, "Execution binding changed"), UNPROCESSABLE(422, "Request cannot be processed"),
    FACTORY_SELECTION_REQUIRED(422, "当前工厂不可用，请重新选择工厂"),
    FACTORY_SCOPE_REQUIRED(400, "Select a factory explicitly"), FACTORY_NOT_FOUND(404, "Factory not found"),
    FACTORY_DISABLED(422, "Factory is disabled"), RUNTIME_NOT_CONFIGURED(422, "Business runtime is not configured"),
    ROUTING_METADATA_UNAVAILABLE(503, "路由元数据暂时不可用，请稍后重试"),
    AUTHENTICATION_UNAVAILABLE(503, "认证服务暂时不可用，请稍后重试"),
    SESSION_REVOCATION_PENDING(503, "账号变更已保存，会话撤销待重试；旧会话准入已拒绝"),
    EXECUTION_UNKNOWN(503, "Database saved; scheduler result UNKNOWN. Retry synchronization or check task logs"), INTERNAL_ERROR(500, "Internal server error");
    private final int httpStatus;
    private final String message;

    ErrorCode(int status, String message) {
        this.httpStatus = status;
        this.message = message;
    }

    public int httpStatus() {
        return httpStatus;
    }

    public String safeMessage() {
        return message;
    }
}
