package dev.mesbase.web.controller.system;

import dev.mesbase.common.core.domain.AjaxResult;
import dev.mesbase.framework.openapi.OpenApiCatalogService;
import dev.mesbase.framework.openapi.OpenApiDataScopeVerification;
import dev.mesbase.platform.web.EndpointAccess;
import dev.mesbase.system.openapi.dto.OpenApiClientApisRequest;
import dev.mesbase.system.openapi.dto.OpenApiClientCreateRequest;
import dev.mesbase.system.openapi.dto.OpenApiClientIdRequest;
import dev.mesbase.system.openapi.dto.OpenApiClientListRequest;
import dev.mesbase.system.openapi.dto.OpenApiClientStatusRequest;
import dev.mesbase.system.openapi.service.OpenApiClientService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 首版仅后台管理员管理；不使用会保存凭证响应的通用 @Log。
 */
@RestController
@RequestMapping("/api/system/open-api/clients")
@ConditionalOnProperty(name = {"mes.system.enabled", "open-api.enabled"}, havingValue = "true")
public class OpenApiClientController {
    private final OpenApiClientService service;
    private final OpenApiCatalogService catalog;
    private final OpenApiDataScopeVerification verification;

    public OpenApiClientController(OpenApiClientService service, OpenApiCatalogService catalog,
                                   OpenApiDataScopeVerification verification) {
        this.service = service;
        this.catalog = catalog;
        this.verification = verification;
    }

    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED, permissionScope = EndpointAccess.PermissionScope.PLATFORM, permission = "system:openApiClient:query")
    @GetMapping("/api-catalog")
    public AjaxResult catalog() {
        return AjaxResult.success(catalog.list());
    }

    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED, permissionScope = EndpointAccess.PermissionScope.PLATFORM, permission = "system:openApiClient:add")
    @PostMapping
    public AjaxResult create(@Valid @RequestBody OpenApiClientCreateRequest request, HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
        return AjaxResult.success(service.create(request));
    }

    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED, permissionScope = EndpointAccess.PermissionScope.PLATFORM, permission = "system:openApiClient:list")
    @GetMapping
    public AjaxResult list(@Valid OpenApiClientListRequest request) {
        return AjaxResult.success(service.list(request));
    }

    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED, permissionScope = EndpointAccess.PermissionScope.PLATFORM, permission = "system:openApiClient:query")
    @GetMapping("/{clientId}")
    public AjaxResult detail(@PathVariable Long clientId) {
        return AjaxResult.success(service.detail(clientId));
    }

    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED, permissionScope = EndpointAccess.PermissionScope.PLATFORM, permission = "system:openApiClient:edit")
    @PutMapping("/status")
    public AjaxResult status(@Valid @RequestBody OpenApiClientStatusRequest request) {
        service.changeStatus(request);
        return AjaxResult.success();
    }

    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED, permissionScope = EndpointAccess.PermissionScope.PLATFORM, permission = "system:openApiClient:reset")
    @PostMapping("/reset-secret")
    public AjaxResult reset(@Valid @RequestBody OpenApiClientIdRequest request, HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
        return AjaxResult.success(service.resetSecret(request));
    }

    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED, permissionScope = EndpointAccess.PermissionScope.PLATFORM, permission = "system:openApiClient:query")
    @GetMapping("/{clientId}/apis")
    public AjaxResult apis(@PathVariable Long clientId) {
        return AjaxResult.success(service.apiCodes(clientId));
    }

    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED, permissionScope = EndpointAccess.PermissionScope.PLATFORM, permission = "system:openApiClient:authorize")
    @PutMapping("/apis")
    public AjaxResult apis(@Valid @RequestBody OpenApiClientApisRequest request) {
        service.replaceApis(request);
        return AjaxResult.success();
    }

    @GetMapping("/data-scope")
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED, permission = "system:openApiClient:list", permissionScope = EndpointAccess.PermissionScope.PLATFORM)
    public AjaxResult dataScope() {
        return AjaxResult.success(new DataScopeStatus(verification.isEnabled()));
    }

    public record DataScopeStatus(boolean enabled) {
    }
}
