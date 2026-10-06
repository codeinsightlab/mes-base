package dev.mesbase.web.controller.system;
import dev.mesbase.server.managed.ManagedDatasourceService;
import dev.mesbase.platform.web.EndpointAccess;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
@RestController @ConditionalOnProperty(name="mes.runtime.enabled",havingValue="true")
@RequestMapping("/api/system/datasource")
public class ManagedDatasourceController extends dev.mesbase.common.core.controller.BaseController {
 private final ManagedDatasourceService service;
 public ManagedDatasourceController(ManagedDatasourceService service){this.service=service;}
 @GetMapping @EndpointAccess(value=EndpointAccess.Kind.AUTHORIZED,permission="system:datasource:list",permissionScope=EndpointAccess.PermissionScope.PLATFORM)
 public dev.mesbase.common.core.page.TableDataInfo list(@RequestParam(defaultValue="1") int pageNum,@RequestParam(defaultValue="20") int pageSize){return getDataTable(service.pageSources(pageNum,pageSize));}
 @GetMapping("/{id}") @EndpointAccess(value=EndpointAccess.Kind.AUTHORIZED,permission="system:datasource:query",permissionScope=EndpointAccess.PermissionScope.PLATFORM)
 public Object detail(@PathVariable long id){return service.detail(id);}
 @PostMapping @EndpointAccess(value=EndpointAccess.Kind.AUTHORIZED,permission="system:datasource:add",permissionScope=EndpointAccess.PermissionScope.PLATFORM)
 public Object create(@Valid @RequestBody ManagedDatasourceService.DatasourceForm form){return service.save(form,true);}
 @PutMapping @EndpointAccess(value=EndpointAccess.Kind.AUTHORIZED,permission="system:datasource:edit",permissionScope=EndpointAccess.PermissionScope.PLATFORM)
 public Object update(@Valid @RequestBody ManagedDatasourceService.DatasourceForm form){return service.save(form,false);}
 @PostMapping("/{id}/enable") @EndpointAccess(value=EndpointAccess.Kind.AUTHORIZED,permission="system:datasource:edit",permissionScope=EndpointAccess.PermissionScope.PLATFORM)
 public Object enable(@PathVariable long id){return service.status(id,true);}
 @PostMapping("/{id}/disable") @EndpointAccess(value=EndpointAccess.Kind.AUTHORIZED,permission="system:datasource:edit",permissionScope=EndpointAccess.PermissionScope.PLATFORM)
 public Object disable(@PathVariable long id){return service.status(id,false);}
 @GetMapping("/{id}/ddl") @EndpointAccess(value=EndpointAccess.Kind.AUTHORIZED,permission="system:datasource:query",permissionScope=EndpointAccess.PermissionScope.PLATFORM)
 public Object ddl(@PathVariable long id){return service.ddl(id,false);}
 @PostMapping("/{id}/ddl/refresh") @EndpointAccess(value=EndpointAccess.Kind.AUTHORIZED,permission="system:datasource:edit",permissionScope=EndpointAccess.PermissionScope.PLATFORM)
 public Object refreshDdl(@PathVariable long id){return service.ddl(id,true);}
 @DeleteMapping("/{id}") @EndpointAccess(value=EndpointAccess.Kind.AUTHORIZED,permission="system:datasource:remove",permissionScope=EndpointAccess.PermissionScope.PLATFORM)
 public void delete(@PathVariable long id){service.delete(id);}
}
