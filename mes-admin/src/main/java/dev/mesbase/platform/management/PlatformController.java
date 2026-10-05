package dev.mesbase.platform.management;
import org.springframework.web.bind.annotation.*;import jakarta.validation.Valid;
import dev.mesbase.platform.management.PlatformContracts.*;
import dev.mesbase.platform.web.*;import dev.mesbase.platform.execution.FactoryContext;
import org.springframework.security.core.Authentication;
import java.util.List;
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name="mes.platform.enabled",havingValue="true")
@RestController @org.springframework.context.annotation.Conditional(dev.mesbase.server.LegacyPlatformCondition.class) @RequestMapping("/api/platform")
public final class PlatformController {
 private final PlatformService service;private final JobService jobs;
 public PlatformController(PlatformService service,JobService jobs){this.service=service;this.jobs=jobs;}
 @GetMapping("/users") @EndpointAccess(value=EndpointAccess.Kind.AUTHORIZED,permission="platform:users:read",permissionScope=EndpointAccess.PermissionScope.PLATFORM)
 public PageView<UserView> users(@RequestParam(defaultValue="0") int offset,@RequestParam(defaultValue="20") int limit){return service.usersPage(offset,limit);}
 @PostMapping("/users") @EndpointAccess(value=EndpointAccess.Kind.AUTHORIZED,permission="platform:users:write",permissionScope=EndpointAccess.PermissionScope.PLATFORM)
 public UserView createusers(@Valid @RequestBody NewUser input){return service.createUser(input);}
 @PutMapping("/users/{id}") @EndpointAccess(value=EndpointAccess.Kind.AUTHORIZED,permission="platform:users:write",permissionScope=EndpointAccess.PermissionScope.PLATFORM)
 public UserView updateusers(@PathVariable String id,@Valid @RequestBody UserInput input){return service.updateUser(id,input);}
 @DeleteMapping("/users/{id}") @EndpointAccess(value=EndpointAccess.Kind.AUTHORIZED,permission="platform:users:write",permissionScope=EndpointAccess.PermissionScope.PLATFORM)
 @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT) public void deleteusers(@PathVariable String id){service.deleteUser(id);}
 @GetMapping("/roles") @EndpointAccess(value=EndpointAccess.Kind.AUTHORIZED,permission="platform:roles:read",permissionScope=EndpointAccess.PermissionScope.PLATFORM)
 public PageView<RoleView> roles(@RequestParam(defaultValue="0") int offset,@RequestParam(defaultValue="20") int limit){return service.rolesPage(offset,limit);}
 @PostMapping("/roles") @EndpointAccess(value=EndpointAccess.Kind.AUTHORIZED,permission="platform:roles:write",permissionScope=EndpointAccess.PermissionScope.PLATFORM)
 public RoleView createroles(@Valid @RequestBody RoleInput input){return service.saveRole(null,input);}
 @PutMapping("/roles/{id}") @EndpointAccess(value=EndpointAccess.Kind.AUTHORIZED,permission="platform:roles:write",permissionScope=EndpointAccess.PermissionScope.PLATFORM)
 public RoleView updateroles(@PathVariable String id,@Valid @RequestBody RoleInput input){return service.saveRole(id,input);}
 @DeleteMapping("/roles/{id}") @EndpointAccess(value=EndpointAccess.Kind.AUTHORIZED,permission="platform:roles:write",permissionScope=EndpointAccess.PermissionScope.PLATFORM)
 @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT) public void deleteroles(@PathVariable String id){service.deleteRole(id);}
 @GetMapping("/menus") @EndpointAccess(value=EndpointAccess.Kind.AUTHORIZED,permission="platform:menus:read",permissionScope=EndpointAccess.PermissionScope.PLATFORM)
 public PageView<MenuView> menus(@RequestParam(defaultValue="0") int offset,@RequestParam(defaultValue="20") int limit){return service.menusPage(offset,limit);}
 @PostMapping("/menus") @EndpointAccess(value=EndpointAccess.Kind.AUTHORIZED,permission="platform:menus:write",permissionScope=EndpointAccess.PermissionScope.PLATFORM)
 public MenuView createmenus(@Valid @RequestBody MenuInput input){return service.saveMenu(null,input);}
 @PutMapping("/menus/{id}") @EndpointAccess(value=EndpointAccess.Kind.AUTHORIZED,permission="platform:menus:write",permissionScope=EndpointAccess.PermissionScope.PLATFORM)
 public MenuView updatemenus(@PathVariable String id,@Valid @RequestBody MenuInput input){return service.saveMenu(id,input);}
 @DeleteMapping("/menus/{id}") @EndpointAccess(value=EndpointAccess.Kind.AUTHORIZED,permission="platform:menus:write",permissionScope=EndpointAccess.PermissionScope.PLATFORM)
 @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT) public void deletemenus(@PathVariable String id){service.deleteMenu(id);}
 @GetMapping("/dict-types") @EndpointAccess(value=EndpointAccess.Kind.AUTHORIZED,permission="platform:dict-types:read",permissionScope=EndpointAccess.PermissionScope.PLATFORM)
 public PageView<DictTypeView> dicttypes(@RequestParam(defaultValue="0") int offset,@RequestParam(defaultValue="20") int limit){return service.typesPage(offset,limit);}
 @PostMapping("/dict-types") @EndpointAccess(value=EndpointAccess.Kind.AUTHORIZED,permission="platform:dict-types:write",permissionScope=EndpointAccess.PermissionScope.PLATFORM)
 public DictTypeView createdicttypes(@Valid @RequestBody DictTypeInput input){return service.saveType(null,input);}
 @PutMapping("/dict-types/{id}") @EndpointAccess(value=EndpointAccess.Kind.AUTHORIZED,permission="platform:dict-types:write",permissionScope=EndpointAccess.PermissionScope.PLATFORM)
 public DictTypeView updatedicttypes(@PathVariable String id,@Valid @RequestBody DictTypeInput input){return service.saveType(id,input);}
 @DeleteMapping("/dict-types/{id}") @EndpointAccess(value=EndpointAccess.Kind.AUTHORIZED,permission="platform:dict-types:write",permissionScope=EndpointAccess.PermissionScope.PLATFORM)
 @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT) public void deletedicttypes(@PathVariable String id){service.deleteType(id);}
 @GetMapping("/dict-data") @EndpointAccess(value=EndpointAccess.Kind.AUTHORIZED,permission="platform:dict-data:read",permissionScope=EndpointAccess.PermissionScope.PLATFORM)
 public PageView<DictDataView> dictdata(@RequestParam(defaultValue="0") int offset,@RequestParam(defaultValue="20") int limit){return service.dataPage(offset,limit);}
 @PostMapping("/dict-data") @EndpointAccess(value=EndpointAccess.Kind.AUTHORIZED,permission="platform:dict-data:write",permissionScope=EndpointAccess.PermissionScope.PLATFORM)
 public DictDataView createdictdata(@Valid @RequestBody DictDataInput input){return service.saveData(null,input);}
 @PutMapping("/dict-data/{id}") @EndpointAccess(value=EndpointAccess.Kind.AUTHORIZED,permission="platform:dict-data:write",permissionScope=EndpointAccess.PermissionScope.PLATFORM)
 public DictDataView updatedictdata(@PathVariable String id,@Valid @RequestBody DictDataInput input){return service.saveData(id,input);}
 @DeleteMapping("/dict-data/{id}") @EndpointAccess(value=EndpointAccess.Kind.AUTHORIZED,permission="platform:dict-data:write",permissionScope=EndpointAccess.PermissionScope.PLATFORM)
 @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT) public void deletedictdata(@PathVariable String id){service.deleteData(id);}
 @GetMapping("/configs") @EndpointAccess(value=EndpointAccess.Kind.AUTHORIZED,permission="platform:configs:read",permissionScope=EndpointAccess.PermissionScope.PLATFORM)
 public PageView<ConfigView> configs(@RequestParam(defaultValue="0") int offset,@RequestParam(defaultValue="20") int limit){return service.configsPage(offset,limit);}
 @PostMapping("/configs") @EndpointAccess(value=EndpointAccess.Kind.AUTHORIZED,permission="platform:configs:write",permissionScope=EndpointAccess.PermissionScope.PLATFORM)
 public ConfigView createconfigs(@Valid @RequestBody ConfigInput input){return service.saveConfig(null,input);}
 @PutMapping("/configs/{id}") @EndpointAccess(value=EndpointAccess.Kind.AUTHORIZED,permission="platform:configs:write",permissionScope=EndpointAccess.PermissionScope.PLATFORM)
 public ConfigView updateconfigs(@PathVariable String id,@Valid @RequestBody ConfigInput input){return service.saveConfig(id,input);}
 @DeleteMapping("/configs/{id}") @EndpointAccess(value=EndpointAccess.Kind.AUTHORIZED,permission="platform:configs:write",permissionScope=EndpointAccess.PermissionScope.PLATFORM)
 @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT) public void deleteconfigs(@PathVariable String id){service.deleteConfig(id);}
 @PutMapping("/users/{id}/password") @EndpointAccess(value=EndpointAccess.Kind.AUTHORIZED,permission="platform:users:write",permissionScope=EndpointAccess.PermissionScope.PLATFORM)
 public void password(@PathVariable String id,@Valid @RequestBody PasswordInput in){service.password(id,in);}
 @PutMapping("/users/{id}/roles") @EndpointAccess(value=EndpointAccess.Kind.AUTHORIZED,permission="platform:users:write",permissionScope=EndpointAccess.PermissionScope.PLATFORM)
 public void userRoles(@PathVariable String id,@Valid @RequestBody Links in){service.userRoles(id,in);}
 @PutMapping("/roles/{id}/menus") @EndpointAccess(value=EndpointAccess.Kind.AUTHORIZED,permission="platform:roles:write",permissionScope=EndpointAccess.PermissionScope.PLATFORM)
 public void roleMenus(@PathVariable String id,@Valid @RequestBody Links in){service.roleMenus(id,in);}
 @GetMapping("/me") @EndpointAccess(EndpointAccess.Kind.AUTHENTICATED) public UserView me(Authentication actor){return service.me(actor.getName());}
 @GetMapping("/navigation") @EndpointAccess(value=EndpointAccess.Kind.AUTHENTICATED,permissionScope=EndpointAccess.PermissionScope.PLATFORM) public Navigation navigation(Authentication actor,@RequestParam(defaultValue="FACTORY") dev.mesbase.infrastructure.platform.PlatformRows.JobScope scope){return service.navigation(actor.getName(),scope==dev.mesbase.infrastructure.platform.PlatformRows.JobScope.PLATFORM?null:FactoryContext.current().map(s->s.factoryId().value()).orElse(null));}
 @GetMapping("/dictionary/{code}") @EndpointAccess(EndpointAccess.Kind.AUTHENTICATED) public List<DictDataView> dictionary(@PathVariable String code){return service.dictionary(code);}
 @GetMapping("/config/{key}") @EndpointAccess(value=EndpointAccess.Kind.AUTHORIZED,permission="platform:configs:read",permissionScope=EndpointAccess.PermissionScope.PLATFORM)
 public java.util.Optional<ConfigView> config(@PathVariable String key){return service.config(FactoryContext.current().map(s->s.factoryId().value()).orElse(null),key);}
 @GetMapping("/audit") @EndpointAccess(value=EndpointAccess.Kind.AUTHORIZED,permission="platform:logs:read",permissionScope=EndpointAccess.PermissionScope.PLATFORM)
 public PageView<AuditView> audits(@RequestParam(defaultValue="0")int offset,@RequestParam(defaultValue="20")int limit){return service.auditsPage(offset,limit);}
 @GetMapping("/login-logs") @EndpointAccess(value=EndpointAccess.Kind.AUTHORIZED,permission="platform:logs:read",permissionScope=EndpointAccess.PermissionScope.PLATFORM)
 public PageView<LoginLogView> logins(@RequestParam(defaultValue="0")int offset,@RequestParam(defaultValue="20")int limit){return service.loginsPage(offset,limit);}
 @GetMapping("/job-logs") @EndpointAccess(value=EndpointAccess.Kind.AUTHORIZED,permission="platform:logs:read",permissionScope=EndpointAccess.PermissionScope.PLATFORM)
 public PageView<JobLogView> jobLogs(@RequestParam(defaultValue="0")int offset,@RequestParam(defaultValue="20")int limit){return service.jobLogsPage(offset,limit);}
 @GetMapping("/jobs") @EndpointAccess(value=EndpointAccess.Kind.AUTHORIZED,permission="platform:jobs:read",permissionScope=EndpointAccess.PermissionScope.PLATFORM)
 public PageView<JobView> jobs(@RequestParam(defaultValue="0")int offset,@RequestParam(defaultValue="20")int limit){return jobs.listPage(offset,limit);}
 @GetMapping("/job-handlers") @EndpointAccess(value=EndpointAccess.Kind.AUTHORIZED,permission="platform:jobs:read",permissionScope=EndpointAccess.PermissionScope.PLATFORM)
 public List<String> handlers(){return jobs.handlerNames();}
 @PostMapping("/jobs") @EndpointAccess(value=EndpointAccess.Kind.AUTHORIZED,permission="platform:jobs:write",permissionScope=EndpointAccess.PermissionScope.PLATFORM)
 public JobView job(@Valid @RequestBody JobInput in){return jobs.save(null,in);}
 @PutMapping("/jobs/{id}") @EndpointAccess(value=EndpointAccess.Kind.AUTHORIZED,permission="platform:jobs:write",permissionScope=EndpointAccess.PermissionScope.PLATFORM)
 public JobView jobUpdate(@PathVariable String id,@Valid @RequestBody JobInput in){return jobs.save(id,in);}
 @DeleteMapping("/jobs/{id}") @EndpointAccess(value=EndpointAccess.Kind.AUTHORIZED,permission="platform:jobs:write",permissionScope=EndpointAccess.PermissionScope.PLATFORM)
 public void jobDelete(@PathVariable String id){jobs.delete(id);}
 @PostMapping("/jobs/{id}/run") @EndpointAccess(value=EndpointAccess.Kind.AUTHORIZED,permission="platform:jobs:run",permissionScope=EndpointAccess.PermissionScope.PLATFORM)
 public void run(@PathVariable String id,Authentication actor,jakarta.servlet.http.HttpServletRequest request){jobs.runForActor(id,actor.getName(),(String)request.getAttribute("mes.requestId"));}
 @GetMapping("/users/{id}/roles") @EndpointAccess(value=EndpointAccess.Kind.AUTHORIZED,permission="platform:users:read",permissionScope=EndpointAccess.PermissionScope.PLATFORM) public LinksView userRoleIds(@PathVariable String id){return service.userRoleIds(id);}
 @GetMapping("/roles/{id}/menus") @EndpointAccess(value=EndpointAccess.Kind.AUTHORIZED,permission="platform:roles:read",permissionScope=EndpointAccess.PermissionScope.PLATFORM) public LinksView roleMenuIds(@PathVariable String id){return service.roleMenuIds(id);}
}
