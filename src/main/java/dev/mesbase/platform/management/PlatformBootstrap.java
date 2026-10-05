package dev.mesbase.platform.management;
import java.util.*;import dev.mesbase.infrastructure.platform.PlatformRows.*;
import dev.mesbase.platform.web.EndpointAccess;
import org.springframework.security.crypto.password.PasswordEncoder;
/** Explicit first-admin provisioning only; no boot-time accounts, ID magic or wildcard permissions. */
public final class PlatformBootstrap {
 private final PlatformStore store;private final PasswordEncoder encoder;
 public PlatformBootstrap(PlatformStore store,PasswordEncoder encoder){this.store=store;this.encoder=encoder;}
 public String provision(String username,String password){if(username==null||!username.matches("[A-Za-z0-9_.-]{3,64}")||password==null||password.length()<12||password.length()>128)throw new IllegalArgumentException("Invalid provisioning input");
  var permissionCodes=new TreeSet<String>();for(var method:PlatformController.class.getDeclaredMethods()){var a=method.getAnnotation(EndpointAccess.class);if(a!=null&&a.value()==EndpointAccess.Kind.AUTHORIZED&&a.permissionScope()==EndpointAccess.PermissionScope.PLATFORM)permissionCodes.add(a.permission());}
  return store.run(m->{m.bumpCatalogRevision();if(m.userByUsername(username)!=null||m.countUser()!=0)throw new dev.mesbase.core.error.FoundationException(dev.mesbase.core.error.ErrorCode.RESOURCE_CONFLICT);String userId=PlatformService.id();m.insertUser(new User(userId,username,"Platform Administrator",encoder.encode(password),Status.ENABLED,PlatformService.now()));String roleId=PlatformService.id();m.insertRole(new Role(roleId,"Platform Administrator",RoleKind.PLATFORM,null,Status.ENABLED));var routes=new LinkedHashMap<String,String>();routes.put("users","用户管理");routes.put("roles","角色与范围");routes.put("menus","菜单与权限");routes.put("dict-types","字典类型");routes.put("dict-data","字典内容");routes.put("configs","参数配置");routes.put("jobs","任务调度");routes.put("audit","操作审计");routes.put("login-logs","登录日志");routes.put("job-logs","执行日志");int order=0;
   for(var entry:routes.entrySet()){String code=entry.getKey().equals("audit")||entry.getKey().endsWith("logs")?"platform:logs:read":"platform:"+entry.getKey()+":read";String id=PlatformService.id();m.insertMenu(new Menu(id,null,entry.getValue(),"/platform/"+entry.getKey(),code,order++,MenuKind.MENU));m.linkRoleMenu(roleId,id);}
   for(String permission:permissionCodes){String id=PlatformService.id();m.insertMenu(new Menu(id,null,permission,"/",permission,order++,MenuKind.BUTTON));m.linkRoleMenu(roleId,id);}m.linkUserRole(userId,roleId);m.insertAudit(new Audit(PlatformService.id(),UUID.randomUUID().toString(),"BOOTSTRAP",null,JobScope.PLATFORM,"INITIAL_ADMIN_PROVISION","SUCCEEDED",PlatformService.now()));return userId;});
 }
}
