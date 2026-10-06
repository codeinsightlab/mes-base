package dev.mesbase.web.controller.system;
import dev.mesbase.server.managed.ManagedDatasourceService;
import dev.mesbase.platform.web.EndpointAccess;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
@RestController @ConditionalOnProperty(name="mes.runtime.enabled",havingValue="true")
@RequestMapping("/api/system/factory")
public class ManagedFactoryController extends dev.mesbase.common.core.controller.BaseController {
 private final ManagedDatasourceService service;
 public ManagedFactoryController(ManagedDatasourceService service){this.service=service;}
 @GetMapping @EndpointAccess(value=EndpointAccess.Kind.AUTHORIZED,permission="system:factory:list",permissionScope=EndpointAccess.PermissionScope.PLATFORM)
 public dev.mesbase.common.core.page.TableDataInfo list(@RequestParam(defaultValue="1") int pageNum,@RequestParam(defaultValue="20") int pageSize){return getDataTable(service.pageFactories(pageNum,pageSize));}
 @GetMapping("/{id}") @EndpointAccess(value=EndpointAccess.Kind.AUTHORIZED,permission="system:factory:query",permissionScope=EndpointAccess.PermissionScope.PLATFORM)
 public Object detail(@PathVariable String id){return service.factory(id);}
 @PostMapping @EndpointAccess(value=EndpointAccess.Kind.AUTHORIZED,permission="system:factory:add",permissionScope=EndpointAccess.PermissionScope.PLATFORM)
 public Object create(@Valid @RequestBody ManagedDatasourceService.FactoryForm form){return service.saveFactory(form,true);}
 @PutMapping @EndpointAccess(value=EndpointAccess.Kind.AUTHORIZED,permission="system:factory:edit",permissionScope=EndpointAccess.PermissionScope.PLATFORM)
 public Object update(@Valid @RequestBody ManagedDatasourceService.FactoryForm form){return service.saveFactory(form,false);}
 @DeleteMapping("/{id}") @EndpointAccess(value=EndpointAccess.Kind.AUTHORIZED,permission="system:factory:remove",permissionScope=EndpointAccess.PermissionScope.PLATFORM)
 public void delete(@PathVariable String id,@RequestParam long revision){service.deleteFactory(id,revision);}
}
