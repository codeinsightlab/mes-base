package dev.mesbase.web.controller.system;

import dev.mesbase.server.managed.ManagedDatasourceService;
import dev.mesbase.server.integration.e10.FactoryOapiService;
import dev.mesbase.platform.web.EndpointAccess;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import dev.mesbase.common.core.controller.BaseController;
import dev.mesbase.common.core.page.TableDataInfo;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

@RestController
@ConditionalOnProperty(name = "mes.runtime.enabled", havingValue = "true")
@RequestMapping("/api/system/factory")
public class ManagedFactoryController extends BaseController {
    private final ManagedDatasourceService service;
    private final FactoryOapiService oapi;

    public ManagedFactoryController(ManagedDatasourceService service, FactoryOapiService oapi) {
        this.service = service;
        this.oapi = oapi;
    }

    @GetMapping("/{id}/oapi")
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED, permission = "system:factory:edit", permissionScope = EndpointAccess.PermissionScope.PLATFORM)
    public FactoryOapiService.View oapi(@PathVariable String id) {
        return oapi.get(id);
    }

    @PutMapping("/{id}/oapi")
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED, permission = "system:factory:edit", permissionScope = EndpointAccess.PermissionScope.PLATFORM)
    public FactoryOapiService.View saveOapi(@PathVariable String id, @Valid @RequestBody FactoryOapiService.Form form) {
        return oapi.save(id, form);
    }

    @GetMapping
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED, permission = "system:factory:list", permissionScope = EndpointAccess.PermissionScope.PLATFORM)
    public TableDataInfo list(@RequestParam(defaultValue = "1") int pageNum,
                              @RequestParam(defaultValue = "20") int pageSize) {
        return getDataTable(service.pageFactories(pageNum, pageSize));
    }

    @GetMapping("/integration-options")
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED, permission = "system:factory:list", permissionScope = EndpointAccess.PermissionScope.PLATFORM)
    public Object integrationOptions(@RequestParam String factoryId) {
        return service.integrationChoices(factoryId);
    }

    @GetMapping("/{id}")
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED, permission = "system:factory:query", permissionScope = EndpointAccess.PermissionScope.PLATFORM)
    public Object detail(@PathVariable String id) {
        return service.factory(id);
    }

    @PostMapping
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED, permission = "system:factory:add", permissionScope = EndpointAccess.PermissionScope.PLATFORM)
    public Object create(@Valid @RequestBody ManagedDatasourceService.FactoryForm form) {
        return service.saveFactory(form, true);
    }

    @PutMapping
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED, permission = "system:factory:edit", permissionScope = EndpointAccess.PermissionScope.PLATFORM)
    public Object update(@Valid @RequestBody ManagedDatasourceService.FactoryForm form) {
        return service.saveFactory(form, false);
    }

    @DeleteMapping("/{id}")
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED, permission = "system:factory:remove", permissionScope = EndpointAccess.PermissionScope.PLATFORM)
    public void delete(@PathVariable String id, @RequestParam long revision) {
        service.deleteFactory(id, revision);
    }
}
