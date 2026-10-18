package dev.mesbase.server;

import dev.mesbase.common.core.domain.entity.SysUser;
import dev.mesbase.common.core.domain.entity.SysRole;
import dev.mesbase.common.core.domain.entity.SysDept;
import dev.mesbase.common.core.domain.entity.SysDictData;
import dev.mesbase.common.core.domain.entity.SysDictType;
import dev.mesbase.common.core.domain.model.LoginUser;
import dev.mesbase.common.core.redis.CenterSettingsCache;
import dev.mesbase.common.core.redis.RedisCache;
import dev.mesbase.common.core.redis.UserSessionStore;
import dev.mesbase.common.exception.ServiceException;
import dev.mesbase.common.utils.PasswordPolicy;
import dev.mesbase.common.utils.spring.SpringUtils;
import dev.mesbase.core.factory.FactoryCatalog;
import dev.mesbase.infrastructure.persistence.SpringCommitActions;
import dev.mesbase.system.domain.SysPost;
import dev.mesbase.system.domain.SysConfig;
import dev.mesbase.system.mapper.SysUserMapper;
import dev.mesbase.system.mapper.SysRoleMapper;
import dev.mesbase.system.mapper.SysDeptMapper;
import dev.mesbase.system.mapper.SysPostMapper;
import dev.mesbase.system.mapper.SysUserRoleMapper;
import dev.mesbase.system.mapper.SysUserPostMapper;
import dev.mesbase.system.mapper.SysRoleDeptMapper;
import dev.mesbase.system.mapper.SessionAccountMapper;
import dev.mesbase.system.mapper.SysConfigMapper;
import dev.mesbase.system.mapper.SysDictDataMapper;
import dev.mesbase.system.mapper.SysDictTypeMapper;
import dev.mesbase.system.service.ISysUserService;
import dev.mesbase.system.service.ISysRoleService;
import dev.mesbase.system.service.ISysConfigService;
import dev.mesbase.system.service.ISysDictDataService;
import dev.mesbase.system.service.ISysDictTypeService;
import dev.mesbase.system.service.SessionRevocationService;
import dev.mesbase.system.service.impl.SysUserServiceImpl;
import dev.mesbase.system.service.impl.SysRoleServiceImpl;
import dev.mesbase.system.service.impl.SysConfigServiceImpl;
import dev.mesbase.system.service.impl.SysDictDataServiceImpl;
import dev.mesbase.system.service.impl.SysDictTypeServiceImpl;
import jakarta.validation.Validation;
import java.sql.Connection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Signature;
import org.apache.ibatis.executor.statement.StatementHandler;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Production services and MyBatis SQL; H2 MOCK by default, isolated real MYSQL on explicit opt-in. */
class P1CorrectnessTest {
    @Intercepts({@Signature(type = Executor.class, method = "update", args = {MappedStatement.class, Object.class}),
            @Signature(type = StatementHandler.class, method = "prepare", args = {Connection.class, Integer.class})})
    static class Failures implements Interceptor {
        final AtomicReference<String> statement = new AtomicReference<>();
        public Object intercept(Invocation call) throws Throwable {
            if (call.getTarget() instanceof StatementHandler handler) {
                // Only H2 clock syntax differs. MySQL is executed without this rewrite.
                if ("H2".equals(((Connection) call.getArgs()[0]).getMetaData().getDatabaseProductName())) {
                    var sql = handler.getBoundSql();
                    ReflectionTestUtils.setField(sql, "sql", sql.getSql().replace("sysdate()", "CURRENT_TIMESTAMP"));
                }
            }
            Object result = call.proceed();
            if (call.getTarget() instanceof Executor) {
                String id = ((MappedStatement) call.getArgs()[0]).getId();
                if (id.equals(statement.get())) {
                    statement.set(null);
                    throw new IllegalStateException("TEST ONLY failure after SQL");
                }
            }
            return result;
        }
    }

    static final class Harness implements AutoCloseable {
        final P1TestDatabase db = new P1TestDatabase();
        final DataSourceTransactionManager manager = new DataSourceTransactionManager(db.source);
        final Failures failures = new Failures();
        final SqlSessionTemplate sessions;
        final GenericApplicationContext beans = new GenericApplicationContext();
        final RedisCache redis;
        final UserSessionStore sessionStore = mock(UserSessionStore.class);
        final CenterSettingsCache cache;
        final ISysUserService users;
        final ISysRoleService roles;
        final ISysConfigService configs;
        final ISysDictDataService dictData;
        final ISysDictTypeService dictTypes;

        Harness() throws Exception {
            this(mock(RedisCache.class));
        }

        Harness(RedisCache redis) throws Exception {
            this.redis = redis;
            this.cache = new CenterSettingsCache(redis, new SpringCommitActions());
            db.table("sys_user", SysUser.class, "user_id");
            db.jdbc.execute("ALTER TABLE sys_user ALTER COLUMN auth_revision SET DEFAULT 0");
            db.jdbc.execute("ALTER TABLE sys_user ALTER COLUMN del_flag SET DEFAULT '0'");
            db.table("sys_role", SysRole.class, "role_id");
            db.table("sys_dept", SysDept.class, "dept_id");
            db.table("sys_post", SysPost.class, "post_id");
            db.table("sys_config", SysConfig.class, "config_id");
            db.jdbc.execute("ALTER TABLE sys_config ADD kind VARCHAR(16) DEFAULT 'GLOBAL'");
            db.jdbc.execute("ALTER TABLE sys_config ADD factory_id VARCHAR(64)");
            db.table("sys_dict_data", SysDictData.class, "dict_code");
            db.table("sys_dict_type", SysDictType.class, "dict_id");
            db.jdbc.execute("CREATE TABLE sys_user_role(user_id BIGINT,role_id BIGINT,PRIMARY KEY(user_id,role_id))");
            db.jdbc.execute("CREATE TABLE sys_user_post(user_id BIGINT,post_id BIGINT,PRIMARY KEY(user_id,post_id))");
            db.jdbc.execute("CREATE TABLE sys_role_dept(role_id BIGINT,dept_id BIGINT,PRIMARY KEY(role_id,dept_id))");
            db.jdbc.execute("CREATE TABLE sys_session_revocation(user_id BIGINT PRIMARY KEY,revision BIGINT,attempts INT DEFAULT 0,"
                    + "last_attempt TIMESTAMP NULL,last_error VARCHAR(128),next_attempt_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");
            db.jdbc.update("INSERT INTO sys_dept(dept_id,dept_name,parent_id,order_num,status,del_flag) VALUES(1,'TEST dept',0,0,'0','0'),(2,'TEST dept2',0,0,'0','0')");
            db.jdbc.update("INSERT INTO sys_role(role_id,role_name,role_key,role_sort,kind,data_scope,status,del_flag) VALUES(1,'TEST role','test',1,'PLATFORM','2','0','0')");
            db.jdbc.update("INSERT INTO sys_post(post_id,post_name,status) VALUES(1,'TEST post','0')");
            db.jdbc.update("INSERT INTO sys_role_dept VALUES(1,1)");
            var config = new org.apache.ibatis.session.Configuration();
            config.getTypeAliasRegistry().registerAliases("dev.mesbase.common.core.domain.entity");
            config.getTypeAliasRegistry().registerAliases("dev.mesbase.system.domain");
            config.setMapUnderscoreToCamelCase(true);
            config.addInterceptor(failures);
            for (var mapper : Set.of(SysUserMapper.class, SysRoleMapper.class, SysDeptMapper.class, SysPostMapper.class,
                    SysUserRoleMapper.class, SysUserPostMapper.class, SysRoleDeptMapper.class, SessionAccountMapper.class,
                    SysConfigMapper.class, SysDictDataMapper.class, SysDictTypeMapper.class)) config.addMapper(mapper);
            var factory = new SqlSessionFactoryBean();
            factory.setDataSource(db.source);
            factory.setConfiguration(config);
            sessions = new SqlSessionTemplate(factory.getObject());
            beans.registerBean(CenterSettingsCache.class, () -> cache);
            beans.refresh();
            new SpringUtils().postProcessBeanFactory(beans.getDefaultListableBeanFactory());
            var validator = Validation.buildDefaultValidatorFactory().getValidator();
            var user = new SysUserServiceImpl();
            ReflectionTestUtils.setField(user, "userMapper", mapper(SysUserMapper.class));
            ReflectionTestUtils.setField(user, "userRoleMapper", mapper(SysUserRoleMapper.class));
            ReflectionTestUtils.setField(user, "userPostMapper", mapper(SysUserPostMapper.class));
            ReflectionTestUtils.setField(user, "linkedRoles", mapper(SysRoleMapper.class));
            ReflectionTestUtils.setField(user, "linkedPosts", mapper(SysPostMapper.class));
            ReflectionTestUtils.setField(user, "contractValidator", validator);
            ReflectionTestUtils.setField(user, "validator", validator);
            ReflectionTestUtils.setField(user, "passwords", new PasswordPolicy(new MockEnvironment()));
            ReflectionTestUtils.setField(user, "revocations", new SessionRevocationService(mapper(SessionAccountMapper.class), sessionStore, manager));
            users = proxy(user, manager);
            var provider = new org.springframework.beans.factory.support.StaticListableBeanFactory();
            provider.addBean("users", users);
            ReflectionTestUtils.setField(user, "self", provider.getBeanProvider(ISysUserService.class));
            var role = new SysRoleServiceImpl();
            ReflectionTestUtils.setField(role, "roleMapper", mapper(SysRoleMapper.class));
            ReflectionTestUtils.setField(role, "roleDeptMapper", mapper(SysRoleDeptMapper.class));
            ReflectionTestUtils.setField(role, "departments", mapper(SysDeptMapper.class));
            ReflectionTestUtils.setField(role, "factories", mock(FactoryCatalog.class));
            roles = proxy(role, manager);
            var settings = new SysConfigServiceImpl();
            ReflectionTestUtils.setField(settings, "contractValidator", validator);
            ReflectionTestUtils.setField(settings, "configMapper", mapper(SysConfigMapper.class));
            ReflectionTestUtils.setField(settings, "settingsCache", cache);
            configs = proxy(settings, manager);
            var data = new SysDictDataServiceImpl();
            ReflectionTestUtils.setField(data, "contractValidator", validator);
            ReflectionTestUtils.setField(data, "dictDataMapper", mapper(SysDictDataMapper.class));
            dictData = proxy(data, manager);
            var type = new SysDictTypeServiceImpl();
            ReflectionTestUtils.setField(type, "contractValidator", validator);
            ReflectionTestUtils.setField(type, "dictDataMapper", mapper(SysDictDataMapper.class));
            ReflectionTestUtils.setField(type, "dictTypeMapper", mapper(SysDictTypeMapper.class));
            dictTypes = proxy(type, manager);
            var identity = new SysUser();
            identity.setUserId(999L);
            identity.setUserName("TEST operator");
            SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                    new LoginUser(999L, 1L, identity, Set.of()), null, List.of()));
        }
        <T> T mapper(Class<T> type) { return sessions.getMapper(type); }
        public void close() { SecurityContextHolder.clearContext(); beans.close(); db.close(); }
    }

    @SuppressWarnings("unchecked")
    static <T> T proxy(T target, DataSourceTransactionManager manager) {
        var proxy = new ProxyFactory(target);
        proxy.setProxyTargetClass(true);
        proxy.setExposeProxy(true);
        proxy.addAdvice(new TransactionInterceptor(manager, new AnnotationTransactionAttributeSource()));
        return (T) proxy.getProxy();
    }

    static SysUser user(String name) {
        var user = new SysUser();
        user.setUserName(name);
        user.setNickName(name);
        user.setStatus("0");
        user.setRoleIds(new Long[]{1L});
        user.setPostIds(new Long[]{1L});
        return user;
    }
    static SysRole role(Long... depts) {
        var role = new SysRole();
        role.setRoleId(1L);
        role.setKind("PLATFORM");
        role.setDataScope("2");
        role.setDeptIds(depts);
        return role;
    }

    @Test
    void importEachRowRollsBackRolePostAccountAndOutboxFailuresAndCanRetry() throws Exception {
        try (var h = new Harness()) {
            for (String point : List.of("SysUserRoleMapper.batchUserRole", "SysUserPostMapper.batchUserPost", "SysUserMapper.insertUser")) {
                h.failures.statement.set("dev.mesbase.system.mapper." + point);
                assertThrows(ServiceException.class, () -> h.users.importUser(List.of(user("failed")), true, "TEST", "Test-only-password-42"));
                assertEquals(0, h.db.jdbc.queryForObject("SELECT COUNT(*) FROM sys_user", Integer.class));
                assertEquals(0, h.db.jdbc.queryForObject("SELECT COUNT(*) FROM sys_user_role", Integer.class));
                assertEquals(0, h.db.jdbc.queryForObject("SELECT COUNT(*) FROM sys_user_post", Integer.class));
            }
            assertTrue(h.users.importUser(List.of(user("retry")), true, "TEST", "Test-only-password-42").contains("全部导入成功"));
            long id = h.db.jdbc.queryForObject("SELECT user_id FROM sys_user WHERE user_name='retry'", Long.class);
            for (String point : List.of("SysUserMapper.updateUser", "SessionAccountMapper.enqueue")) {
                h.failures.statement.set("dev.mesbase.system.mapper." + point);
                var change = user("retry");
                change.setNickName("SHOULD_ROLL_BACK");
                change.setUserId(id);
                assertThrows(RuntimeException.class, () -> h.users.updateUser(change));
                assertEquals("retry", h.db.jdbc.queryForObject("SELECT nick_name FROM sys_user WHERE user_id=?", String.class, id));
                assertEquals(1, h.db.jdbc.queryForObject("SELECT COUNT(*) FROM sys_user_role WHERE user_id=?", Integer.class, id));
                assertEquals(1, h.db.jdbc.queryForObject("SELECT COUNT(*) FROM sys_user_post WHERE user_id=?", Integer.class, id));
                assertEquals(0, h.db.jdbc.queryForObject("SELECT COUNT(*) FROM sys_session_revocation", Integer.class));
            }
            h.users.updateUser(h.mapper(SysUserMapper.class).selectUserById(id));
            verify(h.sessionStore).revoke(eq(id), anyLong());
            assertEquals(1, h.db.jdbc.queryForObject("SELECT COUNT(*) FROM sys_session_revocation", Integer.class));
        }
    }

    @Test
    void middleImportFailureKeepsOnlyCompleteSuccessfulRowsAndRejectsOversizeBeforeWriting() throws Exception {
        try (var h = new Harness()) {
            h.failures.statement.set("dev.mesbase.system.mapper.SysUserRoleMapper.batchUserRole");
            assertThrows(ServiceException.class, () -> h.users.importUser(List.of(user("first_failure"), user("success")), true, "TEST", "Test-only-password-42"));
            assertEquals(List.of("success"), h.db.jdbc.queryForList("SELECT user_name FROM sys_user", String.class));
            assertEquals(1, h.db.jdbc.queryForObject("SELECT COUNT(*) FROM sys_user_role", Integer.class));
            assertThrows(ServiceException.class, () -> h.users.importUser(java.util.Collections.nCopies(201, user("oversize")), true, "TEST", "Test-only-password-42"));
            assertEquals(1, h.db.jdbc.queryForObject("SELECT COUNT(*) FROM sys_user", Integer.class));
        }
    }

    @Test
    void roleFailurePreservesScopeAndDepartmentsAndConcurrentReplacementNeverMixes() throws Exception {
        try (var h = new Harness(); var threads = Executors.newFixedThreadPool(2)) {
            assertThrows(ServiceException.class, () -> h.roles.authDataScope(role(999L)));
            assertThrows(ServiceException.class, () -> h.roles.authDataScope(role(1L, 1L)));
            for (String point : List.of("SysRoleDeptMapper.deleteRoleDeptByRoleId", "SysRoleDeptMapper.batchRoleDept")) {
                h.failures.statement.set("dev.mesbase.system.mapper." + point);
                assertThrows(RuntimeException.class, () -> h.roles.authDataScope(role(2L)));
                assertEquals(List.of(1L), h.db.jdbc.queryForList("SELECT dept_id FROM sys_role_dept WHERE role_id=1", Long.class));
            }
            var start = new CountDownLatch(1);
            var a = threads.submit(() -> { start.await(); h.roles.authDataScope(role(1L)); return null; });
            var b = threads.submit(() -> { start.await(); h.roles.authDataScope(role(2L)); return null; });
            start.countDown();
            a.get(10, TimeUnit.SECONDS); b.get(10, TimeUnit.SECONDS);
            var result = h.db.jdbc.queryForList("SELECT dept_id FROM sys_role_dept WHERE role_id=1", Long.class);
            assertTrue(result.equals(List.of(1L)) || result.equals(List.of(2L)));
            assertEquals("2", h.db.jdbc.queryForObject("SELECT data_scope FROM sys_role WHERE role_id=1", String.class));
        }
    }

    @Test
    void configRollbackRenameAndWriteTransactionMissNeverPublishUncommittedValues() throws Exception {
        try (var h = new Harness()) {
            var config = new SysConfig();
            config.setConfigName("TEST config"); config.setConfigKey("test.old"); config.setConfigValue("before"); config.setConfigType("N");
            h.configs.insertConfig(config);
            config.setConfigId(h.db.jdbc.queryForObject("SELECT config_id FROM sys_config", Long.class));
            verify(h.redis).deleteObject("committed-v1:sys_config:test.old");
            reset(h.redis);
            var tx = new TransactionTemplate(h.manager);
            assertThrows(IllegalStateException.class, () -> tx.executeWithoutResult(status -> {
                config.setConfigKey("test.new"); config.setConfigValue("uncommitted");
                h.configs.updateConfig(config);
                assertEquals("uncommitted", h.configs.selectConfigByKey("test.new"));
                verifyNoInteractions(h.redis);
                throw new IllegalStateException("TEST rollback");
            }));
            assertEquals("before", h.configs.selectConfigByKey("test.old"));
            reset(h.redis);
            h.configs.updateConfig(config);
            verify(h.redis).deleteObject("committed-v1:sys_config:test.old");
            verify(h.redis).deleteObject("committed-v1:sys_config:test.new");
            doThrow(new IllegalStateException("TEST Redis unavailable")).when(h.redis).deleteObject(anyString());
            config.setConfigValue("committed");
            assertDoesNotThrow(() -> h.configs.updateConfig(config));
            assertEquals("committed", h.db.jdbc.queryForObject("SELECT config_value FROM sys_config WHERE config_id=?", String.class, config.getConfigId()));
            reset(h.redis);
            h.configs.selectConfigByKey("sys.account.registerUser");
            verifyNoInteractions(h.redis);
        }
    }
    @Test
    void commitFailureAndBatchDictionaryFailureLeaveDatabaseAndCacheUnpublished() throws Exception {
        try (var h = new Harness()) {
            var failing = new DataSourceTransactionManager(h.db.source) {
                @Override
                protected void doCommit(org.springframework.transaction.support.DefaultTransactionStatus status) {
                    throw new org.springframework.transaction.TransactionSystemException("TEST commit unavailable");
                }
            };
            failing.setRollbackOnCommitFailure(true);
            var target = new SysConfigServiceImpl();
            ReflectionTestUtils.setField(target, "contractValidator", Validation.buildDefaultValidatorFactory().getValidator());
            ReflectionTestUtils.setField(target, "configMapper", h.mapper(SysConfigMapper.class));
            ReflectionTestUtils.setField(target, "settingsCache", h.cache);
            var service = P1CorrectnessTest.proxy(target, failing);
            var config = new SysConfig();
            config.setConfigName("TEST commit"); config.setConfigKey("test.commit"); config.setConfigValue("MUST_NOT_PUBLISH");
            assertThrows(RuntimeException.class, () -> service.insertConfig(config));
            assertEquals(0, h.db.jdbc.queryForObject("SELECT COUNT(*) FROM sys_config", Integer.class));
            verifyNoInteractions(h.redis);
            h.db.jdbc.update("INSERT INTO sys_dict_data(dict_code,dict_type,dict_label,dict_value,dict_sort,status) VALUES(1,'test','one','1',1,'0'),(2,'test','two','2',2,'0')");
            h.failures.statement.set("dev.mesbase.system.mapper.SysDictDataMapper.deleteDictDataById");
            assertThrows(RuntimeException.class, () -> h.dictData.deleteDictDataByIds(new Long[]{1L, 2L}));
            assertEquals(2, h.db.jdbc.queryForObject("SELECT COUNT(*) FROM sys_dict_data", Integer.class));
            verifyNoInteractions(h.redis);
        }
    }

}
