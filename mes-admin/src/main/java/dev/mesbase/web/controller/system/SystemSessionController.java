package dev.mesbase.web.controller.system;
import org.springframework.web.bind.annotation.*;import dev.mesbase.platform.web.*;import dev.mesbase.common.core.domain.AjaxResult;import dev.mesbase.common.core.domain.model.LoginUser;import dev.mesbase.common.core.redis.RedisCache;import dev.mesbase.framework.web.service.TokenService;import dev.mesbase.system.service.*;import jakarta.servlet.http.HttpServletRequest;import jakarta.validation.Valid;import jakarta.validation.constraints.*;
@RestController @org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name="mes.system.enabled",havingValue="true")
public class SystemSessionController {
 public record Login(@NotBlank @Size(max=64) String username,@NotBlank @Size(max=128) String password,String code,String uuid){}
 public record Session(String accessToken,java.time.Instant expiresAt){}
 @org.springframework.beans.factory.annotation.Autowired private dev.mesbase.common.utils.PasswordPolicy passwords;
 private final TokenService tokens;private final SystemPermissionService permissions;private final ISysMenuService menus;private final dev.mesbase.framework.web.service.SysLoginService login;
 public SystemSessionController(TokenService tokens,SystemPermissionService permissions,ISysMenuService menus,dev.mesbase.framework.web.service.SysLoginService login){this.tokens=tokens;this.permissions=permissions;this.menus=menus;this.login=login;}
 @PostMapping("/api/platform/session") @EndpointAccess(EndpointAccess.Kind.PUBLIC)
 public dev.mesbase.framework.web.service.SysLoginService.Session login(@Valid @RequestBody Login input,HttpServletRequest request){return login.login(input.username(),input.password(),input.code(),input.uuid(),request);}
 @DeleteMapping("/api/platform/session") @EndpointAccess(value=EndpointAccess.Kind.AUTHENTICATED,permissionScope=EndpointAccess.PermissionScope.PLATFORM)
 public void logout(){tokens.delLoginUser(dev.mesbase.common.utils.SecurityUtils.getLoginUser().getToken());}
 @GetMapping("/api/system/info") @EndpointAccess(value=EndpointAccess.Kind.AUTHENTICATED,permissionScope=EndpointAccess.PermissionScope.PLATFORM)
 public AjaxResult info(){var a=AjaxResult.success();a.put("user",dev.mesbase.common.utils.SecurityUtils.getLoginUser().getUser());a.put("passwordPolicy",passwords.view());a.put("permissions",permissions.permissions(dev.mesbase.common.utils.SecurityUtils.getUsername(),null));return a;}
 @GetMapping("/api/system/factory-navigation") @EndpointAccess(EndpointAccess.Kind.AUTHENTICATED)
 public Object factoryNavigation(){return new FactoryNavigation(permissions.permissions(dev.mesbase.common.utils.SecurityUtils.getUsername(),dev.mesbase.platform.execution.FactoryContext.required().factoryId().value()),java.util.List.of());}
 public record FactoryNavigation(java.util.Set<String> permissions,java.util.List<Object> menus){}
 @GetMapping("/api/system/getRouters") @EndpointAccess(value=EndpointAccess.Kind.AUTHENTICATED,permissionScope=EndpointAccess.PermissionScope.PLATFORM)
 public AjaxResult routers(){return AjaxResult.success(menus.buildMenus(menus.selectMenuTreeByUserId(dev.mesbase.common.utils.SecurityUtils.getUserId())));}
}