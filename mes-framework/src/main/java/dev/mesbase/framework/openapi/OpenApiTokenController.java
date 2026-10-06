package dev.mesbase.framework.openapi;

import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;

import dev.mesbase.common.core.domain.AjaxResult;
import dev.mesbase.system.openapi.dto.OpenApiCredentialRequest;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

@RestController
@Tag(name = "应用认证")
@RequestMapping("/open-api/token")
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name={"mes.system.enabled","open-api.enabled"},havingValue="true")
public class OpenApiTokenController {
    private final OpenApiTokenService service;
    public OpenApiTokenController(OpenApiTokenService service) { this.service = service; }
    @dev.mesbase.platform.web.EndpointAccess(value=dev.mesbase.platform.web.EndpointAccess.Kind.PUBLIC,permissionScope=dev.mesbase.platform.web.EndpointAccess.PermissionScope.PLATFORM)
    @PostMapping
    @Operation(summary = "获取访问 Token", description = "使用 appKey 与 appSecret 获取访问凭证。有效期内重复申请返回同一个 Token 并重置有效期；普通 API 调用不续期。凭证只应在调用方服务端保存。", security = {})
    @SecurityRequirements
    @ApiResponse(responseCode = "200", description = "成功",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = OpenApiDocumentResponse.TokenResponse.class)))
    public AjaxResult issue(@Valid @RequestBody OpenApiCredentialRequest credentials) {
        return AjaxResult.success(service.issue(credentials));
    }
    @dev.mesbase.platform.web.EndpointAccess(value=dev.mesbase.platform.web.EndpointAccess.Kind.AUTHENTICATED,permissionScope=dev.mesbase.platform.web.EndpointAccess.PermissionScope.PLATFORM)
    @PostMapping("/revoke")
    @Operation(summary = "撤销当前 Token", description = "携带当前访问 Token。撤销后该 Token 无法继续调用，需重新申请。")
    @ApiResponse(responseCode = "200", description = "成功，data 为 null",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = OpenApiDocumentResponse.EmptyResponse.class)))
    public AjaxResult revoke(HttpServletRequest request) {
        service.revoke(OpenApiHttp.bearer(request)); return AjaxResult.success();
    }
}
