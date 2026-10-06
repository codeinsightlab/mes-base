package dev.mesbase.server;
import dev.mesbase.common.core.domain.entity.*;import dev.mesbase.common.core.redis.RedisCache;
import dev.mesbase.quartz.mapper.*;import dev.mesbase.system.openapi.mapper.*;import dev.mesbase.system.service.*;import dev.mesbase.system.mapper.*;
import dev.mesbase.framework.persistence.*;import dev.mesbase.infrastructure.persistence.*;
import org.springframework.context.annotation.*;import org.springframework.core.env.Environment;import javax.sql.DataSource;
@Configuration @org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name="mes.system.enabled",havingValue="true")
public class SystemConfiguration {
 @Bean(destroyMethod="close") DataSource platformDataSource(Environment e){return DruidPools.create(DruidPools.settings(e),"platform",e.getRequiredProperty("mes.platform.url"),e.getRequiredProperty("mes.platform.username"),e.getRequiredProperty("mes.platform.password"));}
 @Bean PlatformPersistence platformPersistence(DataSource source,Environment e){
  if(e.getProperty("mes.platform.migrate",Boolean.class,false))org.flywaydb.core.Flyway.configure().dataSource(new org.springframework.jdbc.datasource.DriverManagerDataSource(e.getRequiredProperty("mes.platform.url"),e.getRequiredProperty("mes.platform.username"),e.getRequiredProperty("mes.platform.password"))).locations("classpath:db/platform","classpath:db/system").load().migrate();
  return new PlatformPersistence(source,java.util.Set.of(OpenApiClientMapper.class,OpenApiClientExtraMapper.class,OpenApiClientApiMapper.class,AutoCodeExtraMapper.class,SysAutoCodeRuleMapper.class,SysAutoCodePartMapper.class,SysAutoCodeResultMapper.class,SysMessageMapper.class,SysAttachmentMapper.class,SysJobMapper.class,SysJobLogMapper.class,JobClaimMapper.class,SysUserRecipientExtraMapper.class,SysLogininforMapper.class,SysOperLogMapper.class,SysPostMapper.class,SysDictTypeMapper.class,SysUserPostMapper.class,SysConfigMapper.class,SysRoleMenuMapper.class,SysNoticeMapper.class,SysMenuMapper.class,SysRoleMapper.class,SysDictDataMapper.class,SysDeptMapper.class,SysRoleDeptMapper.class,SysUserMapper.class,SysUserRoleMapper.class),c->{
   c.getTypeAliasRegistry().registerAliases("dev.mesbase.common.core.domain.entity");c.getTypeAliasRegistry().registerAliases("dev.mesbase.system.domain");c.getTypeAliasRegistry().registerAliases("dev.mesbase.system.openapi.domain");c.getTypeAliasRegistry().registerAliases("dev.mesbase.quartz.domain");
   var page=new com.github.pagehelper.PageInterceptor();var settings=new java.util.Properties();settings.setProperty("helperDialect",e.getProperty("pagehelper.helper-dialect","mysql"));settings.setProperty("reasonable",e.getProperty("pagehelper.reasonable","false"));page.setProperties(settings);c.addInterceptor(page);
  });
 }
 @Bean dev.mesbase.framework.security.SystemTokenFilter systemTokens(dev.mesbase.framework.web.service.TokenService t){return new dev.mesbase.framework.security.SystemTokenFilter(t);}
 @Bean org.springframework.boot.web.servlet.FilterRegistrationBean<dev.mesbase.framework.security.SystemTokenFilter> systemTokenRegistration(dev.mesbase.framework.security.SystemTokenFilter f){var r=new org.springframework.boot.web.servlet.FilterRegistrationBean<>(f);r.setEnabled(false);return r;}
 @Bean OpenApiClientMapper openApiClientMapper(SystemUnit u){return u.mapper(OpenApiClientMapper.class);}
 @Bean OpenApiClientExtraMapper openApiClientExtraMapper(SystemUnit u){return u.mapper(OpenApiClientExtraMapper.class);}
 @Bean OpenApiClientApiMapper openApiClientApiMapper(SystemUnit u){return u.mapper(OpenApiClientApiMapper.class);}
 @Bean SysJobMapper sysJobMapper(SystemUnit u){return u.mapper(SysJobMapper.class);}
 @Bean SysJobLogMapper sysJobLogMapper(SystemUnit u){return u.mapper(SysJobLogMapper.class);}
 @Bean JobClaimMapper jobClaimMapper(SystemUnit u){return u.mapper(JobClaimMapper.class);}
 @Bean dev.mesbase.quartz.util.ControlledJobRunner controlledJobs(SystemUnit u,dev.mesbase.core.factory.FactoryCatalog f){return new dev.mesbase.quartz.util.ControlledJobRunner(u,f);}
 @Bean(destroyMethod="shutdown") org.quartz.Scheduler systemScheduler(Environment env)throws org.quartz.SchedulerException{var props=new java.util.Properties();props.setProperty("org.quartz.scheduler.instanceName","MESBaseSystem");props.setProperty("org.quartz.threadPool.threadCount",env.getProperty("spring.quartz.properties.org.quartz.threadPool.threadCount","2"));return new org.quartz.impl.StdSchedulerFactory(props).getScheduler();}
 @Bean org.springframework.beans.factory.SmartInitializingSingleton warmSystemCache(ISysDictTypeService dicts,ISysConfigService configs){return ()->{dicts.loadingDictCache();configs.loadingConfigCache();};}
 @Bean org.springframework.beans.factory.SmartInitializingSingleton startSystemJobs(dev.mesbase.quartz.service.ISysJobService jobs,org.quartz.Scheduler scheduler){return ()->{try{((dev.mesbase.quartz.service.impl.SysJobServiceImpl)jobs).init();scheduler.start();}catch(Exception failure){throw new IllegalStateException("Quartz initialization failed",failure);}};}
 @Bean AutoCodeExtraMapper autoCodeExtraMapper(SystemUnit unit){return unit.mapper(AutoCodeExtraMapper.class);}
 @Bean SysAutoCodeRuleMapper sysAutoCodeRuleMapper(SystemUnit unit){return unit.mapper(SysAutoCodeRuleMapper.class);}
 @Bean SysAutoCodePartMapper sysAutoCodePartMapper(SystemUnit unit){return unit.mapper(SysAutoCodePartMapper.class);}
 @Bean SysAutoCodeResultMapper sysAutoCodeResultMapper(SystemUnit unit){return unit.mapper(SysAutoCodeResultMapper.class);}
 @Bean SysMessageMapper sysMessageMapper(SystemUnit u){return u.mapper(SysMessageMapper.class);}
 @Bean SysAttachmentMapper sysAttachmentMapper(SystemUnit u){return u.mapper(SysAttachmentMapper.class);}
 @Bean dev.mesbase.platform.management.FileStorage systemFileStorage(Environment e){String kind=e.getRequiredProperty("mes.storage.type");if("minio".equals(kind))return new dev.mesbase.infrastructure.platform.MinioPrivateFileStorage(e.getRequiredProperty("minio.url"),e.getRequiredProperty("minio.access-key"),e.getRequiredProperty("minio.secret-key"),e.getRequiredProperty("minio.bucket-name"));if(!"local".equals(kind))throw new IllegalArgumentException("Unsupported private storage type");return new dev.mesbase.infrastructure.platform.LocalPrivateFileStorage(java.nio.file.Path.of(e.getRequiredProperty("mes.platform.file-root")));}
 @Bean SystemUnit systemUnit(PlatformPersistence p){return new SystemUnit(p);}
 @Bean SystemUnitAspect unitAspect(SystemUnit u){return new SystemUnitAspect(u);}
 @Bean SysLogininforMapper sysLogininforMapper(SystemUnit u){return u.mapper(SysLogininforMapper.class);}
@Bean SysOperLogMapper sysOperLogMapper(SystemUnit u){return u.mapper(SysOperLogMapper.class);}
@Bean SysPostMapper sysPostMapper(SystemUnit u){return u.mapper(SysPostMapper.class);}
@Bean SysDictTypeMapper sysDictTypeMapper(SystemUnit u){return u.mapper(SysDictTypeMapper.class);}
@Bean SysUserPostMapper sysUserPostMapper(SystemUnit u){return u.mapper(SysUserPostMapper.class);}
@Bean SysConfigMapper sysConfigMapper(SystemUnit u){return u.mapper(SysConfigMapper.class);}
@Bean SysRoleMenuMapper sysRoleMenuMapper(SystemUnit u){return u.mapper(SysRoleMenuMapper.class);}
@Bean SysNoticeMapper sysNoticeMapper(SystemUnit u){return u.mapper(SysNoticeMapper.class);}
@Bean SysMenuMapper sysMenuMapper(SystemUnit u){return u.mapper(SysMenuMapper.class);}
@Bean SysRoleMapper sysRoleMapper(SystemUnit u){return u.mapper(SysRoleMapper.class);}
@Bean SysDictDataMapper sysDictDataMapper(SystemUnit u){return u.mapper(SysDictDataMapper.class);}
@Bean SysDeptMapper sysDeptMapper(SystemUnit u){return u.mapper(SysDeptMapper.class);}
@Bean SysRoleDeptMapper sysRoleDeptMapper(SystemUnit u){return u.mapper(SysRoleDeptMapper.class);}
@Bean SysUserMapper sysUserMapper(SystemUnit u){return u.mapper(SysUserMapper.class);}
@Bean SysUserRoleMapper sysUserRoleMapper(SystemUnit u){return u.mapper(SysUserRoleMapper.class);}
 @Bean SysUserRecipientExtraMapper sysUserRecipientExtraMapper(SystemUnit u){return u.mapper(SysUserRecipientExtraMapper.class);}
 @Bean org.springframework.security.crypto.password.PasswordEncoder passwords(){return org.springframework.security.crypto.factory.PasswordEncoderFactories.createDelegatingPasswordEncoder();}
 @Bean @Primary org.springframework.security.core.userdetails.UserDetailsService systemAccounts(ISysUserService users){return name->{var user=users.selectUserByUserName(name);if(user==null || !"0".equals(user.getStatus()) || !"0".equals(user.getDelFlag()))throw new org.springframework.security.core.userdetails.UsernameNotFoundException("Account not found");return new dev.mesbase.common.core.domain.model.LoginUser(user.getUserId(),user.getDeptId(),user,java.util.Set.of());};}
 @Bean @Primary dev.mesbase.core.security.AuthorizationService factoryAuthorization(SystemPermissionService p){return (actor,scope,permission)->p.granted(actor.id(),scope.factoryId().value(),permission.code());}
 @Bean dev.mesbase.platform.security.PlatformAuthorization platformAuthorization(SystemPermissionService p){return (actor,permission)->p.granted(actor.id(),null,permission.code());}
}