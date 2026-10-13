package dev.mesbase.server;

import dev.mesbase.business.production.dao.ProductionBatchMapper;
import dev.mesbase.business.production.dao.WorkOrderExtraMapper;
import dev.mesbase.business.production.dao.BatchRouteNodeMapper;
import dev.mesbase.business.production.dao.RoutingMapper;
import dev.mesbase.business.production.dao.RoutingProductMapper;
import dev.mesbase.business.production.dao.RoutingNodeMapper;
import dev.mesbase.business.production.dao.RoutingEdgeMapper;
import dev.mesbase.business.basic.dao.MaterialMapper;
import dev.mesbase.business.issue.dao.MesMaterialIssueMapper;
import dev.mesbase.business.production.service.RoutingReferenceRules;

import dev.mesbase.infrastructure.cache.BusinessCacheProcessor;

import jakarta.validation.Validator;


import dev.mesbase.framework.sync.SyncCoordinator;


import org.springframework.beans.factory.support.DefaultListableBeanFactory;

import dev.mesbase.common.core.domain.model.LoginUser;
import dev.mesbase.common.core.redis.UserSessionStore;
import dev.mesbase.core.factory.Factory;
import dev.mesbase.core.factory.FactoryCatalog;
import dev.mesbase.core.factory.FactoryId;
import dev.mesbase.core.factory.FactorySelectionService;
import dev.mesbase.framework.security.SystemTokenFilter;
import dev.mesbase.framework.web.service.TokenService;
import dev.mesbase.infrastructure.catalog.InMemoryFactoryPreference;
import dev.mesbase.infrastructure.persistence.BusinessRuntimeBinding;
import dev.mesbase.infrastructure.persistence.DatasourceIdentity;
import dev.mesbase.infrastructure.persistence.FactoryBusinessDataSource;
import dev.mesbase.infrastructure.persistence.OwnedTransactionManager;
import dev.mesbase.infrastructure.persistence.PersistenceFactories;
import dev.mesbase.infrastructure.persistence.PlatformPersistence;
import dev.mesbase.infrastructure.persistence.RuntimeRegistry;
import dev.mesbase.business.production.controller.WorkOrderController;
import dev.mesbase.business.production.dao.WorkorderMapper;

import dev.mesbase.business.production.service.WorkOrderService;
import dev.mesbase.platform.web.FoundationAccessInterceptor;
import dev.mesbase.platform.web.RequestBoundaryFilter;
import dev.mesbase.server.managed.DatasourcePasswordCipher;
import dev.mesbase.server.managed.ManagedDatasourceService;
import dev.mesbase.server.managed.FactoryMetadataCache;
import dev.mesbase.system.factory.RoutingMetadataMapper;
import dev.mesbase.common.core.redis.RoutingMetadataStore;
import dev.mesbase.system.factory.ManagedDatasourceMapper;
import dev.mesbase.system.factory.ManagedFactoryMapper;
import dev.mesbase.system.mapper.SessionAccountMapper;
import dev.mesbase.system.mapper.SysMenuMapper;
import dev.mesbase.system.mapper.SysRoleMapper;
import dev.mesbase.system.mapper.SysUserMapper;
import dev.mesbase.system.service.SystemPermissionService;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;
import org.apache.ibatis.executor.statement.StatementHandler;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Signature;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.method.HandlerMethod;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;

/**
 * TEST ONLY: real entry/permission/router/workorder SQL, H2 and synthetic Redis payload; no HTTP/network timing claim.
 */
class WorkOrderRequestBudgetTest {
    @Intercepts(@Signature(type = StatementHandler.class, method = "prepare", args = {Connection.class, Integer.class}))
    static class Statements implements Interceptor {
        final List<String> queries = new ArrayList<>();

        public Object intercept(Invocation invocation) throws Throwable {
            queries.add(((StatementHandler) invocation.getTarget()).getBoundSql().getSql());
            return invocation.proceed();
        }
    }

    private DriverManagerDataSource source() {
        return new DriverManagerDataSource("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
    }

    @Test
    void warmPageUsesTwoCenterAndTwoBusinessStatementsWithoutMetadataOrMenuQueries() throws Exception {
        var center = source();
        var business = source();
        var jdbc = new JdbcTemplate(center);
        String migration = Files.readString(Path.of("mes-admin/src/main/resources/db/system/V2__source_product_system.sql"));
        for (String table : List.of("sys_user", "sys_dept", "sys_role", "sys_user_role", "sys_menu", "sys_role_menu")) {
            int start = migration.indexOf("create table " + table + " (");
            jdbc.execute(migration.substring(start, migration.indexOf(';', start)).replaceAll("\\) engine=.*", ")"));
        }
        jdbc.execute("ALTER TABLE sys_user ADD auth_revision BIGINT DEFAULT 1");
        jdbc.execute("ALTER TABLE sys_role ADD kind VARCHAR(10)");
        jdbc.execute("ALTER TABLE sys_role ADD factory_id VARCHAR(64)");
        jdbc.execute("INSERT INTO sys_user(user_id,user_name,nick_name,dept_id,status,del_flag) VALUES(42,'fixture','Fixture',7,'0','0')");
        jdbc.execute("INSERT INTO sys_role(role_id,role_name,role_key,role_sort,status,del_flag,kind,factory_id) VALUES(8,'Fixture','fixture',1,'0','0','FACTORY','A')");
        jdbc.execute("INSERT INTO sys_user_role VALUES(42,8)");
        jdbc.execute("INSERT INTO sys_menu(menu_id,menu_name,parent_id,path,menu_type,perms,status) VALUES(1,'Fixture',0,'pro','C','workorder:list','0')");
        jdbc.execute("INSERT INTO sys_role_menu VALUES(8,1)");
        jdbc.execute("CREATE TABLE mf_factory(factory_id VARCHAR(64),enterprise_id VARCHAR(64),factory_code VARCHAR(32),factory_name VARCHAR(64),datasource_id BIGINT,status VARCHAR(16),revision BIGINT)");
        jdbc.execute("CREATE TABLE mf_datasource(datasource_id BIGINT,datasource_name VARCHAR(64),db_host VARCHAR(64),db_port INT,db_name VARCHAR(64),db_username VARCHAR(64),password_cipher VARCHAR(64),status VARCHAR(16),revision BIGINT)");
        jdbc.execute("INSERT INTO mf_factory VALUES('A','TEST ONLY','A','TEST ONLY',1,'ENABLED',1)");
        jdbc.execute("INSERT INTO mf_datasource VALUES(1,'TEST ONLY','invalid.test',3306,'TEST_ONLY','TEST_ONLY','TEST_ONLY','ENABLED',1)");
        String mapperXml = Files.readString(Path.of("mes-business/src/main/resources/dev/mesbase/business/production/dao/WorkorderMapper.xml"));
        var columns = Pattern.compile("column=\"([^\"]+)\" jdbcType=\"([^\"]+)\"").matcher(mapperXml);
        var definitions = new ArrayList<String>();
        while (columns.find()) definitions.add(columns.group(1) + " " + (columns.group(2)
                .equals("VARCHAR") ? "VARCHAR(255)" : columns.group(2)));
        new JdbcTemplate(business).execute("CREATE TABLE workorder(" + String.join(",", definitions) + ")");
        new JdbcTemplate(business).execute("INSERT INTO workorder(id,code,revision,product_id,routing_id) VALUES(1,'TEST ONLY',0,1,1)");
        for (String type : List.of("ProductionBatch", "Routing")) {
            String xml = Files.readString(Path.of("mes-business/src/main/resources/dev/mesbase/business/production/dao/" + type + "Mapper.xml"));
            var matches = Pattern.compile("column=\"([^\"]+)\" jdbcType=\"([^\"]+)\"").matcher(xml);
            var fields = new ArrayList<String>();
            while (matches.find()) fields.add(matches.group(1) + " " + (matches.group(2)
                    .equals("VARCHAR") ? "VARCHAR(255)" : matches.group(2)));
            new JdbcTemplate(business).execute("CREATE TABLE " + (type.equals("Routing") ? "routing" : "production_batch") + "(" + String.join(",", fields) + ")");
        }
        String xml = Files.readString(Path.of("mes-business/src/main/resources/dev/mesbase/business/basic/dao/MaterialMapper.xml"));
        var matches = Pattern.compile("column=\"([^\"]+)\" jdbcType=\"([^\"]+)\"").matcher(xml);
        var fields = new ArrayList<String>();
        while (matches.find()) fields.add(matches.group(1) + " " + (matches.group(2)
                .equals("VARCHAR") ? "VARCHAR(255)" : matches.group(2)));
        new JdbcTemplate(business).execute("CREATE TABLE material(" + String.join(",", fields) + ")");
        new JdbcTemplate(business).execute("INSERT INTO material(id,code,name) VALUES(1,'TEST-P','TEST ONLY')");
        new JdbcTemplate(business).execute("INSERT INTO routing(id,code,name,version) VALUES(1,'TEST-R','TEST ONLY',1)");
        var centerSql = new Statements();
        var businessSql = new Statements();
        var platform = new PlatformPersistence(center, Set.of(SessionAccountMapper.class, SysUserMapper.class,
                SysMenuMapper.class, SysRoleMapper.class, ManagedFactoryMapper.class, ManagedDatasourceMapper.class, RoutingMetadataMapper.class), c -> {
            c.getTypeAliasRegistry().registerAliases("dev.mesbase.common.core.domain.entity");
            c.addInterceptor(centerSql);
        });
        var accounts = platform.mapper(SessionAccountMapper.class);
        var sessions = mock(UserSessionStore.class);
        var payload = new AtomicReference<String>();
        when(sessions.issue(anyString(), anyLong(), anyLong(), anyString(), any())).thenAnswer(call -> {
            payload.set(call.getArgument(3));
            return true;
        });
        when(sessions.read(anyString())).thenAnswer(call -> payload.get());
        var tokens = new TokenService(sessions, accounts, new MockEnvironment());
        var user = accounts.account(42);
        String raw = tokens.createToken(new LoginUser(42L, 7L, user, Set.of()), new MockHttpServletRequest());
        try (var registry = new RuntimeRegistry()) {
            registry.registerDedicated(new FactoryId("A"), new DatasourceIdentity("managed-1"), 1, business, () -> {
            }, Set.of(WorkorderMapper.class, ProductionBatchMapper.class, MaterialMapper.class, RoutingMapper.class));
            var routingStore = RoutingCacheFixture.store();
            var metadata = new FactoryMetadataCache(platform, routingStore);
            var managed = new ManagedDatasourceService(platform, mock(DatasourcePasswordCipher.class), registry,
                    center, null, Set.of(WorkorderMapper.class, ProductionBatchMapper.class, MaterialMapper.class, RoutingMapper.class), Path.of("target/TEST-ONLY-unused-ddl"), metadata);
            var source = new FactoryBusinessDataSource(managed::resolve, registry);
            var manager = new OwnedTransactionManager(source);
            var factory = PersistenceFactories.create(source, Set.of(WorkorderMapper.class, ProductionBatchMapper.class, MaterialMapper.class, RoutingMapper.class), c -> {
                PersistenceFactories.pagination(c);
                c.addInterceptor(businessSql);
            }, manager::verifyAccess);
            var controller = new WorkOrderController(new WorkOrderService(new SqlSessionTemplate(factory).getMapper(WorkorderMapper.class), new SqlSessionTemplate(factory).getMapper(ProductionBatchMapper.class), mock(BatchRouteNodeMapper.class), new SqlSessionTemplate(factory).getMapper(RoutingMapper.class), mock(RoutingProductMapper.class), mock(RoutingNodeMapper.class), mock(RoutingEdgeMapper.class), new SqlSessionTemplate(factory).getMapper(MaterialMapper.class), mock(RoutingReferenceRules.class), mock(MesMaterialIssueMapper.class), mock(WorkOrderExtraMapper.class)));
            var permissions = new SystemPermissionService(platform.mapper(SysMenuMapper.class), platform.mapper(SysRoleMapper.class), platform.mapper(ManagedFactoryMapper.class));
            FactoryCatalog catalog = new FactoryCatalog() {
                public Optional<Factory> find(FactoryId id) {
                    return managed.find(id);
                }

                public List<Factory> enabled(int offset, int limit) {
                    return managed.enabled(offset, limit);
                }
            };
            var configuration = new SystemConfiguration();
            var access = new FoundationAccessInterceptor(new FactorySelectionService(catalog, new InMemoryFactoryPreference()),
                    configuration.factoryAuthorization(permissions), configuration.platformAuthorization(permissions), managed::admit);
            var handler = new HandlerMethod(controller, WorkOrderController.class.getMethod("list", String.class, String.class, int.class, int.class));
            var times = new ArrayList<Double>();
            for (int i = 0; i < 25; i++) {
                var request = new MockHttpServletRequest("GET", "/api/production/workorder");
                request.addHeader("Authorization", "Bearer " + raw);
                request.addHeader("X-Factory-Id", "A");
                var response = new MockHttpServletResponse();
                clearInvocations(sessions, routingStore);
                centerSql.queries.clear();
                businessSql.queries.clear();
                RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
                long start = System.nanoTime();
                try {
                    new RequestBoundaryFilter().doFilter(request, response, (req, res) ->
                            new SystemTokenFilter(tokens).doFilter(request, response, (authenticated, result) -> {
                                assertTrue(access.preHandle(request, response, handler));
                                var page = new TransactionTemplate(manager).execute(status -> controller.list(null, null, 0, 20));
                                assertEquals(1, page.total());
                            }));
                    assertEquals(200, response.getStatus());
                    verify(sessions, times(1)).read(anyString());
                    verify(routingStore, times(1)).read(anyString());
                    if (i == 0) assertEquals(3, centerSql.queries.size(), centerSql.queries.toString());
                    else assertEquals(2, centerSql.queries.size(), centerSql.queries.toString());
                    assertTrue(centerSql.queries.stream()
                            .noneMatch(sql -> sql.contains("m.parent_id") || sql.contains("selectMenuVo")));
                    if (i > 0) assertTrue(centerSql.queries.stream()
                            .noneMatch(sql -> sql.contains("mf_factory") || sql.contains("mf_datasource")));
                    assertEquals(5, businessSql.queries.size(), businessSql.queries.toString());
                    assertTrue(centerSql.queries.stream().noneMatch(sql -> sql.toLowerCase().contains("u.password")));
                    if (i >= 5) times.add((System.nanoTime() - start) / 1_000_000.0);
                } finally {
                    RequestContextHolder.resetRequestAttributes();
                    SecurityContextHolder.clearContext();
                }
            }
            times.sort(Comparator.naturalOrder());
            Files.createDirectories(Path.of("target/validation"));
            Files.writeString(Path.of("target/validation/workorder-request-budget.json"),
                    "{\"evidence\":\"H2 production chain fixture, synthetic Redis payload, no socket\",\"samples\":20,\"centerQueries\":2,\"businessQueries\":5,\"p50Ms\":" + times.get(9) + ",\"p95Ms\":" + times.get(18) + "}");
        }
    }
}
