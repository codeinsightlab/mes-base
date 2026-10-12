package dev.mesbase.framework.openapi;

import com.fasterxml.jackson.annotation.JsonInclude;
import dev.mesbase.common.openapi.OpenApiError;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 仅用于描述 AjaxResult + OpenApiResponseAdvice 的真实契约，不参与运行时返回。
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public class OpenApiDocumentResponse<T> {
    @Schema(description = "响应状态码，与 HTTP 状态一致", example = "200", required = true)
    public Integer code;
    @Schema(description = "响应说明", example = "操作成功", required = true)
    public String msg;
    @Schema(description = "响应数据，无数据时为 null", required = true, nullable = true)
    public T data;
    @Schema(description = "服务端调用追踪标识", example = "doc-example-request-id", required = true)
    public String requestId;

    public static class TokenResponse extends OpenApiDocumentResponse<OpenApiTokenVo> {
    }

    public static class PingResponse extends OpenApiDocumentResponse<OpenApiPingVo> {
    }

    public static class ClientResponse extends OpenApiDocumentResponse<OpenApiClientIdentityVo> {
    }

    public static class CapabilitiesResponse extends OpenApiDocumentResponse<OpenApiCapabilitiesVo> {
    }

    @Schema(description = "无返回数据，data 为 null")
    public static class EmptyResponse extends OpenApiDocumentResponse<Object> {
    }

    @JsonInclude(JsonInclude.Include.ALWAYS)
    @Schema(description = "错误响应，data 为 null")
    public static class ErrorResponse extends OpenApiDocumentResponse<Object> {
        @Schema(description = "稳定错误码", required = true)
        public OpenApiError errorCode;
    }
}
