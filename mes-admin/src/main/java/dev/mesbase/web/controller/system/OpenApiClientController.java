package dev.mesbase.web.controller.system;

import dev.mesbase.common.core.domain.AjaxResult;
import dev.mesbase.system.openapi.dto.*;
import dev.mesbase.framework.openapi.OpenApiCatalogService;
import dev.mesbase.system.openapi.service.OpenApiClientService;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletResponse;

/** 首版仅后台管理员管理；不使用会保存凭证响应的通用 @Log。 */
@RestController
@RequestMapping("/api/system/open-api/clients")
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name={"mes.system.enabled","open-api.enabled"},havingValue="true")
public class OpenApiClientController {
    private final OpenApiClientService service;
    private final OpenApiCatalogService catalog;
    private final dev.mesbase.framework.openapi.OpenApiDataScopeVerification verification;
    public OpenApiClientController(OpenApiClientService service, OpenApiCatalogService catalog, dev.mesbase.framework.openapi.OpenApiDataScopeVerification verification) {
        this.service = service; this.catalog = catalog; this.verification = verification;
    }
    @dev.mesbase.platform.web.EndpointAccess(value=dev.mesbase.platform.web.EndpointAccess.Kind.AUTHORIZED,permissionScope=dev.mesbase.platform.web.EndpointAccess.PermissionScope.PLATFORM,permission="system:openApiClient:query")
    @GetMapping("/api-catalog")
    public AjaxResult catalog() { return AjaxResult.success(catalog.list()); }
    @dev.mesbase.platform.web.EndpointAccess(value=dev.mesbase.platform.web.EndpointAccess.Kind.AUTHORIZED,permissionScope=dev.mesbase.platform.web.EndpointAccess.PermissionScope.PLATFORM,permission="system:openApiClient:add")
    @PostMapping
    public AjaxResult create(@Valid @RequestBody OpenApiClientCreateRequest request, HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
        return AjaxResult.success(service.create(request));
    }
    @dev.mesbase.platform.web.EndpointAccess(value=dev.mesbase.platform.web.EndpointAccess.Kind.AUTHORIZED,permissionScope=dev.mesbase.platform.web.EndpointAccess.PermissionScope.PLATFORM,permission="system:openApiClient:list")
    @GetMapping
    public AjaxResult list(@Valid OpenApiClientListRequest request) { return AjaxResult.success(service.list(request)); }
    @dev.mesbase.platform.web.EndpointAccess(value=dev.mesbase.platform.web.EndpointAccess.Kind.AUTHORIZED,permissionScope=dev.mesbase.platform.web.EndpointAccess.PermissionScope.PLATFORM,permission="system:openApiClient:query")
    @GetMapping("/{clientId}")
    public AjaxResult detail(@PathVariable Long clientId) { return AjaxResult.success(service.detail(clientId)); }
    @dev.mesbase.platform.web.EndpointAccess(value=dev.mesbase.platform.web.EndpointAccess.Kind.AUTHORIZED,permissionScope=dev.mesbase.platform.web.EndpointAccess.PermissionScope.PLATFORM,permission="system:openApiClient:edit")
    @PutMapping("/status")
    public AjaxResult status(@Valid @RequestBody OpenApiClientStatusRequest request) {
        service.changeStatus(request); return AjaxResult.success();
    }
    @dev.mesbase.platform.web.EndpointAccess(value=dev.mesbase.platform.web.EndpointAccess.Kind.AUTHORIZED,permissionScope=dev.mesbase.platform.web.EndpointAccess.PermissionScope.PLATFORM,permission="system:openApiClient:reset")
    @PostMapping("/reset-secret")
    public AjaxResult reset(@Valid @RequestBody OpenApiClientIdRequest request, HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
        return AjaxResult.success(service.resetSecret(request));
    }
    @dev.mesbase.platform.web.EndpointAccess(value=dev.mesbase.platform.web.EndpointAccess.Kind.AUTHORIZED,permissionScope=dev.mesbase.platform.web.EndpointAccess.PermissionScope.PLATFORM,permission="system:openApiClient:query")
    @GetMapping("/{clientId}/apis")
    public AjaxResult apis(@PathVariable Long clientId) { return AjaxResult.success(service.apiCodes(clientId)); }
    @dev.mesbase.platform.web.EndpointAccess(value=dev.mesbase.platform.web.EndpointAccess.Kind.AUTHORIZED,permissionScope=dev.mesbase.platform.web.EndpointAccess.PermissionScope.PLATFORM,permission="system:openApiClient:authorize")
    @PutMapping("/apis")
    public AjaxResult apis(@Valid @RequestBody OpenApiClientApisRequest request) {
        service.replaceApis(request); return AjaxResult.success();
    }

    @GetMapping("/data-scope")
    @dev.mesbase.platform.web.EndpointAccess(value=dev.mesbase.platform.web.EndpointAccess.Kind.AUTHORIZED,permission="system:openApiClient:list",permissionScope=dev.mesbase.platform.web.EndpointAccess.PermissionScope.PLATFORM)
    public AjaxResult dataScope() {
        return AjaxResult.success(new DataScopeStatus(verification.isEnabled()));
    }
    public record DataScopeStatus(boolean enabled) { }
}
