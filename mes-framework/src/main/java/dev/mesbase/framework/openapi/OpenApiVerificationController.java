package dev.mesbase.framework.openapi;

import dev.mesbase.common.core.domain.AjaxResult;
import dev.mesbase.common.openapi.OpenApiEndpoint;
import dev.mesbase.common.openapi.OpenApiDataScopeIgnore;
import dev.mesbase.common.openapi.OpenApiPrincipal;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * 第二组实际接口，用于检查分组授权效果，不读取MES业务数据。
 */
@OpenApiEndpoint(description = "连接验收")
@RestController
@RequestMapping("/open-api/v1/verification")
@io.swagger.v3.oas.annotations.Hidden
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name={"mes.system.enabled","open-api.enabled"},havingValue="true")
public class OpenApiVerificationController {
    private final OpenApiDiagnosticsService service;

    public OpenApiVerificationController(OpenApiDiagnosticsService service) {
        this.service = service;
    }

    @OpenApiDataScopeIgnore
    @dev.mesbase.platform.web.EndpointAccess(value=dev.mesbase.platform.web.EndpointAccess.Kind.AUTHENTICATED,permissionScope=dev.mesbase.platform.web.EndpointAccess.PermissionScope.PLATFORM)
    @GetMapping("/ping")
    @OpenApiEndpoint(value = "verification.ping", description = "验收连通性：独立授权项，返回pong及服务端UTC时间")
    public AjaxResult ping() {
        return AjaxResult.success(service.ping());
    }

    @OpenApiDataScopeIgnore
    @dev.mesbase.platform.web.EndpointAccess(value=dev.mesbase.platform.web.EndpointAccess.Kind.AUTHENTICATED,permissionScope=dev.mesbase.platform.web.EndpointAccess.PermissionScope.PLATFORM)
    @GetMapping("/client")
    @OpenApiEndpoint(value = "verification.client", description = "验收应用身份：独立授权项，仅返回当前Token对应的appKey")
    public AjaxResult client(@AuthenticationPrincipal OpenApiPrincipal principal) {
        return AjaxResult.success(service.client(principal));
    }
}
