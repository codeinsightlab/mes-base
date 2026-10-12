package dev.mesbase.framework.openapi;

import dev.mesbase.common.core.domain.AjaxResult;
import dev.mesbase.common.openapi.OpenApiDataScopeIgnore;
import dev.mesbase.common.openapi.OpenApiEndpoint;
import dev.mesbase.common.openapi.OpenApiPrincipal;
import dev.mesbase.platform.web.EndpointAccess;
import io.swagger.v3.oas.annotations.Hidden;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 第二组实际接口，用于检查分组授权效果，不读取MES业务数据。
 */
@OpenApiEndpoint(description = "连接验收")
@RestController
@RequestMapping("/open-api/v1/verification")
@Hidden
@ConditionalOnProperty(name = {"mes.system.enabled", "open-api.enabled"}, havingValue = "true")
public class OpenApiVerificationController {
    private final OpenApiDiagnosticsService service;

    public OpenApiVerificationController(OpenApiDiagnosticsService service) {
        this.service = service;
    }

    @OpenApiDataScopeIgnore
    @EndpointAccess(value = EndpointAccess.Kind.AUTHENTICATED, permissionScope = EndpointAccess.PermissionScope.PLATFORM)
    @GetMapping("/ping")
    @OpenApiEndpoint(value = "verification.ping", description = "验收连通性：独立授权项，返回pong及服务端UTC时间")
    public AjaxResult ping() {
        return AjaxResult.success(service.ping());
    }

    @OpenApiDataScopeIgnore
    @EndpointAccess(value = EndpointAccess.Kind.AUTHENTICATED, permissionScope = EndpointAccess.PermissionScope.PLATFORM)
    @GetMapping("/client")
    @OpenApiEndpoint(value = "verification.client", description = "验收应用身份：独立授权项，仅返回当前Token对应的appKey")
    public AjaxResult client(@AuthenticationPrincipal OpenApiPrincipal principal) {
        return AjaxResult.success(service.client(principal));
    }
}
