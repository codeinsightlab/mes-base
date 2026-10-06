package dev.mesbase.common.openapi;

/** 对外稳定错误语义；消息不包含内部异常内容。 */
public enum OpenApiError {
    INVALID_REQUEST(400, "请求参数或方法不正确"),
    INVALID_CREDENTIALS(401, "凭证无效或已过期"),
    ACCESS_DENIED(403, "无接口或业务范围权限"),
    DATA_SCOPE_NOT_CONFIGURED(403, "当前应用尚未配置有效的业务数据权限"),
    RATE_LIMITED(429, "请求过于频繁"),
    DEPENDENCY_UNAVAILABLE(503, "依赖服务暂不可用"),
    INTERNAL_ERROR(500, "系统暂时无法处理请求");

    private final int status;
    private final String name;
    OpenApiError(int status, String name) { this.status = status; this.name = name; }
    public String getCode() { return name(); }
    public String getName() { return name; }
    public int getStatus() { return status; }
}
