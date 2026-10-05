package dev.mesbase.platform.management;
import java.util.*;
import java.time.*;
import dev.mesbase.core.error.*;
import dev.mesbase.core.factory.*;
import dev.mesbase.core.security.*;
import dev.mesbase.infrastructure.platform.PlatformRows;
import dev.mesbase.infrastructure.platform.PlatformRows.*;
import dev.mesbase.infrastructure.platform.PlatformMapper;
import dev.mesbase.platform.management.PlatformContracts.*;
import org.springframework.security.crypto.password.PasswordEncoder;
/** Platform use cases; scopes and ownership are decisions here, parameterized SQL lives in Mapper. */
public final class PlatformService {
 final PlatformStore store; private final FactoryCatalog factories; private final PasswordEncoder encoder;
 // Mutation and cache lookup share a monitor, including publication after DB commit; no stale fill after invalidation.
 private final Map<String,List<DictDataView>> dictCache=new HashMap<>();
 private final Map<ConfigCacheKey,Optional<ConfigView>> configCache=new HashMap<>();
 private long cacheRevision=-1;
 private void refreshCaches(){long version=store.run(m->m.catalogRevision());if(version!=cacheRevision){dictCache.clear();configCache.clear();cacheRevision=version;}}
 private record ConfigCacheKey(String factoryId,String key) {}
 public PlatformService(PlatformStore store,FactoryCatalog factories,PasswordEncoder encoder){this.store=store;this.factories=factories;this.encoder=encoder;}
 static String id(){return UUID.randomUUID().toString();}
 static LocalDateTime now(){return LocalDateTime.now(Clock.systemUTC());}
 static <T> T required(T value){if(value==null)throw new FoundationException(ErrorCode.NOT_FOUND);return value;}
 static void changed(int n){if(n!=1)throw new FoundationException(ErrorCode.RESOURCE_CONFLICT);}
 static void page(int offset,int limit){if(offset<0 || limit<1 || limit>100)throw new FoundationException(ErrorCode.BAD_REQUEST);}
 void factory(String factoryId){if(factoryId==null)throw new FoundationException(ErrorCode.FACTORY_SCOPE_REQUIRED);var f=required(factories.find(new FactoryId(factoryId)).orElse(null));if(f.status()!=FactoryStatus.ENABLED)throw new FoundationException(ErrorCode.FACTORY_DISABLED);}
 static UserView view(User u){return new UserView(u.id(),u.username(),u.displayName(),u.status(),u.createdAt());}
 public List<UserView> users(int offset,int limit){page(offset,limit);return store.run(m->m.listUser(offset,limit).stream().map(PlatformService::view).toList());}
 public UserView createUser(NewUser input){return store.run(m->{var u=new User(id(),input.user().username(),input.user().displayName(),encoder.encode(input.credential().password()),input.user().status(),now());changed(m.insertUser(u));return view(u);});}
 public UserView updateUser(String id,UserInput input){return store.run(m->{var old=required(m.findUser(id));var u=new User(id,input.username(),input.displayName(),old.passwordHash(),input.status(),old.createdAt());changed(m.updateUser(u));if(input.status()==Status.DISABLED)m.revokeUser(id);return view(u);});}
 public void password(String id,PasswordInput input){store.run(m->{var u=required(m.findUser(id));changed(m.updateUser(new User(u.id(),u.username(),u.displayName(),encoder.encode(input.password()),u.status(),u.createdAt())));m.revokeUser(id);return null;});}
 public void deleteUser(String id){store.run(m->{required(m.findUser(id));m.revokeUser(id);m.clearUserRoles(id);changed(m.deleteUser(id));return null;});}
 public UserView me(String username){return store.run(m->view(required(m.userByUsername(username))));}
 public String actorId(String username){return store.run(m->required(m.userByUsername(username)).id());}
 public boolean granted(String username,String factoryId,String permission){return store.run(m->m.granted(username,factoryId,permission)>0);}
 public List<RoleView> roles(int offset,int limit){page(offset,limit);return store.run(m->m.listRole(offset,limit).stream().map(r->new RoleView(r.id(),r.name(),r.kind(),r.factoryId(),r.status())).toList());}
 public RoleView saveRole(String id,RoleInput input){if(input.kind()==RoleKind.FACTORY)factory(input.factoryId());else if(input.factoryId()!=null)throw new FoundationException(ErrorCode.BAD_REQUEST);return store.run(m->{String key=id==null?id():id;var r=new PlatformRows.Role(key,input.name(),input.kind(),input.factoryId(),input.status());if(id==null)changed(m.insertRole(r));else{required(m.findRole(id));changed(m.updateRole(r));}return new RoleView(r.id(),r.name(),r.kind(),r.factoryId(),r.status());});}
 public void deleteRole(String id){store.run(m->{required(m.findRole(id));m.clearRoleMenus(id);changed(m.deleteRole(id));return null;});}
 public void userRoles(String userId,Links links){store.run(m->{required(m.findUser(userId));var ids=links.ids().stream().distinct().toList();if(!ids.isEmpty()&&m.countRoles(ids)!=ids.size())throw new FoundationException(ErrorCode.NOT_FOUND);m.clearUserRoles(userId);if(!ids.isEmpty())m.batchUserRoles(userId,ids);return null;});}
 public void roleMenus(String roleId,Links links){store.run(m->{required(m.findRole(roleId));var ids=links.ids().stream().distinct().toList();if(!ids.isEmpty()&&m.countMenus(ids)!=ids.size())throw new FoundationException(ErrorCode.NOT_FOUND);m.clearRoleMenus(roleId);if(!ids.isEmpty())m.batchRoleMenus(roleId,ids);return null;});}
 private static MenuView menu(Menu m,List<MenuView> children){return new MenuView(m.id(),m.parentId(),m.name(),m.path(),m.permission(),m.sortOrder(),m.kind(),children);}
 private static List<MenuView> tree(List<Menu> all,String parent,Set<String> seen){return all.stream().filter(m->m.kind()==MenuKind.MENU && Objects.equals(m.parentId(),parent)).map(m->{if(!seen.add(m.id()))throw new FoundationException(ErrorCode.BAD_REQUEST);return menu(m,tree(all,m.id(),seen));}).toList();}
 public List<MenuView> menus(int offset,int limit){page(offset,limit);return store.run(m->m.listMenu(offset,limit).stream().map(x->menu(x,List.of())).toList());}
 public Navigation navigation(String username,String factoryId){return store.run(m->{var u=required(m.userByUsername(username));var all=m.grantedMenus(u.id(),factoryId);if(all.size()>1000)throw new FoundationException(ErrorCode.UNPROCESSABLE);var menuIds=all.stream().filter(x->x.kind()==MenuKind.MENU).map(Menu::id).collect(java.util.stream.Collectors.toSet());var normalized=all.stream().map(x->x.parentId()!=null&&!menuIds.contains(x.parentId())?new Menu(x.id(),null,x.name(),x.path(),x.permission(),x.sortOrder(),x.kind()):x).toList();return new Navigation(tree(normalized,null,new HashSet<>()),all.stream().map(Menu::permission).distinct().toList());});}
 public MenuView saveMenu(String id,MenuInput in){new Permission(in.permission());return store.run(m->{String key=id==null?id():id;if(in.parentId()!=null){var p=required(m.findMenu(in.parentId()));var seen=new HashSet<String>();while(p!=null){if(p.id().equals(key)||!seen.add(p.id()))throw new FoundationException(ErrorCode.BAD_REQUEST);p=p.parentId()==null?null:required(m.findMenu(p.parentId()));}}var r=new Menu(key,in.parentId(),in.name(),in.path(),in.permission(),in.sortOrder(),in.kind());if(id==null)changed(m.insertMenu(r));else{required(m.findMenu(id));changed(m.updateMenu(r));}return menu(r,List.of());});}
 public void deleteMenu(String id){store.run(m->{required(m.findMenu(id));changed(m.deleteMenu(id));return null;});}
 public List<DictTypeView> types(int offset,int limit){page(offset,limit);return store.run(m->m.listDictType(offset,limit).stream().map(r->new DictTypeView(r.id(),r.code(),r.name())).toList());}
 public synchronized DictTypeView saveType(String id,DictTypeInput in){var result=store.run(m->{String key=id==null?id():id;var r=new DictType(key,in.code(),in.name());if(id==null)changed(m.insertDictType(r));else{required(m.findDictType(id));changed(m.updateDictType(r));}m.bumpCatalogRevision();return new DictTypeView(r.id(),r.code(),r.name());});dictCache.clear();return result;}
 public synchronized void deleteType(String id){store.run(m->{required(m.findDictType(id));changed(m.deleteDictType(id));m.bumpCatalogRevision();return null;});dictCache.clear();}
 public List<DictDataView> data(int offset,int limit){page(offset,limit);return store.run(m->m.listDictData(offset,limit).stream().map(PlatformService::dataView).toList());}
 static DictDataView dataView(DictData r){return new DictDataView(r.id(),r.typeId(),r.value(),r.label(),r.sortOrder());}
 public synchronized DictDataView saveData(String id,DictDataInput in){var result=store.run(m->{required(m.findDictType(in.typeId()));String key=id==null?id():id;var r=new DictData(key,in.typeId(),in.value(),in.label(),in.sortOrder());if(id==null)changed(m.insertDictData(r));else{required(m.findDictData(id));changed(m.updateDictData(r));}m.bumpCatalogRevision();return dataView(r);});dictCache.clear();return result;}
 public synchronized void deleteData(String id){store.run(m->{required(m.findDictData(id));changed(m.deleteDictData(id));m.bumpCatalogRevision();return null;});dictCache.clear();}
 public synchronized List<DictDataView> dictionary(String code){refreshCaches();if(dictCache.containsKey(code))return dictCache.get(code);var result=store.run(m->{var t=required(m.typeByCode(code));var values=m.dictValues(t.id());if(values.size()>1000)throw new FoundationException(ErrorCode.UNPROCESSABLE);return values.stream().map(PlatformService::dataView).toList();});if(dictCache.size()>=200)dictCache.clear();dictCache.put(code,result);return result;}
 public List<ConfigView> configs(int offset,int limit){page(offset,limit);return store.run(m->m.listConfig(offset,limit).stream().map(PlatformService::configView).toList());}
 static ConfigView configView(Config r){return new ConfigView(r.id(),r.configKey(),r.configValue(),r.kind(),r.factoryId());}
 public synchronized ConfigView saveConfig(String id,ConfigInput in){if(in.kind()==ConfigKind.FACTORY)factory(in.factoryId());else if(in.factoryId()!=null)throw new FoundationException(ErrorCode.BAD_REQUEST);String lower=in.configKey().toLowerCase(Locale.ROOT);if(lower.contains("password")||lower.contains("secret")||lower.contains("token")||lower.contains("credential")||lower.contains("private_key"))throw new FoundationException(ErrorCode.BAD_REQUEST);var result=store.run(m->{String key=id==null?id():id;var r=new Config(key,in.configKey(),in.configValue(),in.kind(),in.factoryId());if(id==null)changed(m.insertConfig(r));else{required(m.findConfig(id));changed(m.updateConfig(r));}m.bumpCatalogRevision();return configView(r);});configCache.clear();return result;}
 public synchronized void deleteConfig(String id){store.run(m->{required(m.findConfig(id));changed(m.deleteConfig(id));m.bumpCatalogRevision();return null;});configCache.clear();}
 public synchronized Optional<ConfigView> config(String factoryId,String key){refreshCaches();var cacheKey=new ConfigCacheKey(factoryId,key);if(configCache.containsKey(cacheKey))return configCache.get(cacheKey);var result=store.run(m->Optional.ofNullable(m.configByKey(factoryId==null?"PLATFORM":"FACTORY:"+factoryId,key)).map(PlatformService::configView));if(configCache.size()>=200)configCache.clear();configCache.put(cacheKey,result);return result;}
 public void audit(String requestId,String actor,String factoryId,String action,String outcome){audit(requestId,actor,factoryId,factoryId==null?JobScope.PLATFORM:JobScope.FACTORY,action,outcome);}
 public void audit(String requestId,String actor,String factoryId,JobScope executionScope,String action,String outcome){store.run(m->{m.insertAudit(new Audit(id(),requestId,actor,factoryId,executionScope,action,outcome,now()));return null;});}
 public List<AuditView> audits(int offset,int limit){page(offset,limit);return store.run(m->m.listAudit(offset,limit).stream().map(r->new AuditView(r.id(),r.requestId(),r.actorId(),r.factoryId(),r.executionScope(),r.action(),r.outcome(),r.createdAt())).toList());}
 public List<LoginLogView> logins(int offset,int limit){page(offset,limit);return store.run(m->m.listLoginLog(offset,limit).stream().map(r->new LoginLogView(r.id(),r.requestId(),r.actorId(),r.factoryId(),r.executionScope(),r.outcome(),r.createdAt())).toList());}
 public List<JobLogView> jobLogs(int offset,int limit){page(offset,limit);return store.run(m->m.listJobLog(offset,limit).stream().map(r->new JobLogView(r.id(),r.jobId(),r.requestId(),r.actorId(),r.factoryId(),r.executionScope(),r.outcome(),r.createdAt())).toList());}
 public PageView<UserView> usersPage(int offset,int limit){var rows=users(offset,limit);return new PageView<>(rows,store.run(m->m.countUser()),offset,limit);}
 public PageView<RoleView> rolesPage(int offset,int limit){var rows=roles(offset,limit);return new PageView<>(rows,store.run(m->m.countRole()),offset,limit);}
 public PageView<MenuView> menusPage(int offset,int limit){var rows=menus(offset,limit);return new PageView<>(rows,store.run(m->m.countMenu()),offset,limit);}
 public PageView<DictTypeView> typesPage(int offset,int limit){var rows=types(offset,limit);return new PageView<>(rows,store.run(m->m.countDictType()),offset,limit);}
 public PageView<DictDataView> dataPage(int offset,int limit){var rows=data(offset,limit);return new PageView<>(rows,store.run(m->m.countDictData()),offset,limit);}
 public PageView<ConfigView> configsPage(int offset,int limit){var rows=configs(offset,limit);return new PageView<>(rows,store.run(m->m.countConfig()),offset,limit);}
 public PageView<AuditView> auditsPage(int offset,int limit){var rows=audits(offset,limit);return new PageView<>(rows,store.run(m->m.countAudit()),offset,limit);}
 public PageView<LoginLogView> loginsPage(int offset,int limit){var rows=logins(offset,limit);return new PageView<>(rows,store.run(m->m.countLoginLog()),offset,limit);}
 public PageView<JobLogView> jobLogsPage(int offset,int limit){var rows=jobLogs(offset,limit);return new PageView<>(rows,store.run(m->m.countJobLog()),offset,limit);}
 public LinksView userRoleIds(String id){return store.run(m->{required(m.findUser(id));var ids=m.userRoleIds(id);if(ids.size()>200)throw new FoundationException(ErrorCode.UNPROCESSABLE);return new LinksView(ids);});}
 public LinksView roleMenuIds(String id){return store.run(m->{required(m.findRole(id));var ids=m.roleMenuIds(id);if(ids.size()>200)throw new FoundationException(ErrorCode.UNPROCESSABLE);return new LinksView(ids);});}
}
