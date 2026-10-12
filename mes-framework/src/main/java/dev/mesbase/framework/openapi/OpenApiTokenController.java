package dev.mesbase.framework.openapi;

import dev.mesbase.common.core.domain.AjaxResult;
import dev.mesbase.platform.web.EndpointAccess;
import dev.mesbase.system.openapi.dto.OpenApiCredentialRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@SecurityScheme(name = "OpenApiBearer", type = SecuritySchemeType.HTTP, scheme = "bearer", description = "外部应用 Token，与后台用户 Token 分离")
@Tag(name = "应用认证")
@RequestMapping("/open-api/token")
@ConditionalOnProperty(name = {"mes.system.enabled", "open-api.enabled"}, havingValue = "true")
public class OpenApiTokenController {
    private final OpenApiTokenService service;

    public OpenApiTokenController(OpenApiTokenService service) {
        this.service = service;
    }

    @EndpointAccess(value = EndpointAccess.Kind.PUBLIC, permissionScope = EndpointAccess.PermissionScope.PLATFORM)
    @PostMapping
    @Operation(summary = "获取访问 Token", description = "使用 appKey 与 appSecret 获取访问凭证。有效期内重复申请返回同一个 Token 并重置有效期；普通 API 调用不续期。凭证只应在调用方服务端保存。", security = {})
    @SecurityRequirements
    @ApiResponse(responseCode = "200", description = "成功",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = OpenApiDocumentResponse.TokenResponse.class)))
    public AjaxResult issue(@Valid @RequestBody OpenApiCredentialRequest credentials) {
        return AjaxResult.success(service.issue(credentials));
    }

    @EndpointAccess(value = EndpointAccess.Kind.AUTHENTICATED, permissionScope = EndpointAccess.PermissionScope.PLATFORM)
    @PostMapping("/revoke")
    @SecurityRequirement(name = "OpenApiBearer")
    @Operation(summary = "撤销当前 Token", description = "携带当前访问 Token。撤销后该 Token 无法继续调用，需重新申请。")
    @ApiResponse(responseCode = "200", description = "成功，data 为 null",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = OpenApiDocumentResponse.EmptyResponse.class)))
    public AjaxResult revoke(HttpServletRequest request) {
        service.revoke(OpenApiHttp.bearer(request));
        return AjaxResult.success();
    }
}
