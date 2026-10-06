package dev.mesbase.framework.openapi;

import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;

import dev.mesbase.common.core.domain.AjaxResult;
import dev.mesbase.common.openapi.OpenApiEndpoint;
import dev.mesbase.common.openapi.OpenApiDataScopeIgnore;
import dev.mesbase.common.openapi.OpenApiPrincipal;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "平台诊断")
@io.swagger.v3.oas.annotations.security.SecurityRequirement(name="OpenApiBearer")
@OpenApiEndpoint(description = "平台诊断")
@RestController
@RequestMapping("/open-api/v1/diagnostics")
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name={"mes.system.enabled","open-api.enabled"},havingValue="true")
public class OpenApiDiagnosticsController {
    private final OpenApiDiagnosticsService service;

    public OpenApiDiagnosticsController(OpenApiDiagnosticsService service) {
        this.service = service;
    }

    @OpenApiDataScopeIgnore
    @dev.mesbase.platform.web.EndpointAccess(value=dev.mesbase.platform.web.EndpointAccess.Kind.AUTHENTICATED,permissionScope=dev.mesbase.platform.web.EndpointAccess.PermissionScope.PLATFORM)
    @GetMapping("/ping")
    @Operation(summary = "连通性检查")
    @ApiResponse(responseCode = "200", description = "成功",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = OpenApiDocumentResponse.PingResponse.class)))
    @OpenApiEndpoint(value = "diagnostics.ping", description = "连通性检查：返回pong及服务端UTC时间，不查询业务数据")
    public AjaxResult ping() {
        return AjaxResult.success(service.ping());
    }

    @OpenApiDataScopeIgnore
    @dev.mesbase.platform.web.EndpointAccess(value=dev.mesbase.platform.web.EndpointAccess.Kind.AUTHENTICATED,permissionScope=dev.mesbase.platform.web.EndpointAccess.PermissionScope.PLATFORM)
    @GetMapping("/client")
    @Operation(summary = "查看当前应用身份")
    @ApiResponse(responseCode = "200", description = "成功",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = OpenApiDocumentResponse.ClientResponse.class)))
    @OpenApiEndpoint(value = "diagnostics.client", description = "当前应用身份：仅返回当前Token对应的appKey")
    public AjaxResult client(@AuthenticationPrincipal OpenApiPrincipal principal) {
        return AjaxResult.success(service.client(principal));
    }

    @OpenApiDataScopeIgnore
    @dev.mesbase.platform.web.EndpointAccess(value=dev.mesbase.platform.web.EndpointAccess.Kind.AUTHENTICATED,permissionScope=dev.mesbase.platform.web.EndpointAccess.PermissionScope.PLATFORM)
    @GetMapping("/capabilities")
    @Operation(summary = "查看当前接口授权")
    @ApiResponse(responseCode = "200", description = "成功",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = OpenApiDocumentResponse.CapabilitiesResponse.class)))
    @OpenApiEndpoint(value = "diagnostics.capabilities", description = "当前应用可调用接口：仅列出已授权且实际注册的API")
    public AjaxResult capabilities(@AuthenticationPrincipal OpenApiPrincipal principal) {
        return AjaxResult.success(service.capabilities(principal));
    }
}
