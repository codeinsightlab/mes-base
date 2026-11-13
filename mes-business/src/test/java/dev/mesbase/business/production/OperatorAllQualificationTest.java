package dev.mesbase.business.production;

import dev.mesbase.business.production.controller.MesOperatorQualificationController;
import dev.mesbase.business.production.controller.ProductionReportingController;
import dev.mesbase.business.production.dao.MesOperationUserMapper;
import dev.mesbase.business.production.dao.MesOperatorQualificationMapper;
import dev.mesbase.business.production.dao.OperatorQualificationExtraMapper;
import dev.mesbase.business.production.dao.OperationMapper;
import dev.mesbase.business.production.dao.RoutingNodeMapper;
import dev.mesbase.business.production.dao.MesProductionExecutionMapper;
import dev.mesbase.business.production.dao.MesExecutionAssignmentMapper;
import dev.mesbase.business.production.domain.MesProductionExecutionEntity;
import dev.mesbase.business.production.domain.RoutingNodeEntity;
import dev.mesbase.business.production.domain.ProductionReportModels.ExecutionQuantity;
import dev.mesbase.business.production.domain.ProductionReportModels.NodeQuantity;
import dev.mesbase.business.production.service.OperationUserDirectory;
import dev.mesbase.business.production.service.OperatorQualificationRules;
import dev.mesbase.business.production.service.OperatorCandidateService;
import dev.mesbase.business.production.service.ProductionChainLocks;
import dev.mesbase.business.production.service.ProductionQuantityService;
import dev.mesbase.business.production.service.ProductionReportSubmitService;
import dev.mesbase.business.production.service.ReportOperatorEligibilityService;
import dev.mesbase.business.production.service.impl.MesOperatorQualificationServiceImpl;
import dev.mesbase.business.production.service.impl.MesOperationUserServiceImpl;
import dev.mesbase.common.core.domain.entity.SysUser;
import dev.mesbase.common.core.domain.entity.SysMenu;
import dev.mesbase.common.core.domain.model.LoginUser;
import dev.mesbase.core.error.ErrorCode;
import dev.mesbase.core.error.FoundationException;
import dev.mesbase.core.factory.FactoryId;
import dev.mesbase.core.factory.FactoryScope;
import dev.mesbase.core.factory.FactorySelectionService;
import dev.mesbase.core.security.AuthorizationService;
import dev.mesbase.infrastructure.persistence.BusinessRuntimeBinding;
import dev.mesbase.infrastructure.persistence.DatasourceIdentity;
import dev.mesbase.infrastructure.persistence.FactoryBusinessDataSource;
import dev.mesbase.infrastructure.persistence.RuntimeRegistry;
import dev.mesbase.infrastructure.persistence.OwnedTransactionManager;
import dev.mesbase.infrastructure.persistence.PersistenceFactories;
import dev.mesbase.infrastructure.persistence.PlatformPersistence;
import dev.mesbase.platform.execution.FactoryContext;
import dev.mesbase.platform.web.FoundationAccessInterceptor;
import dev.mesbase.system.mapper.SysMenuMapper;
import dev.mesbase.system.production.OperationUserDirectoryMapper;
import dev.mesbase.business.production.service.ProductionIdentityService;
import dev.mesbase.business.production.service.ExecutionPathService;
import dev.mesbase.business.production.service.BusinessSourceRules;
import dev.mesbase.business.production.service.MaterialQuantityRules;
import dev.mesbase.business.production.dao.MesProductionReportMapper;
import dev.mesbase.business.production.dao.WorkorderMapper;
import dev.mesbase.business.basic.dao.WorkstationMapper;
import dev.mesbase.business.production.domain.ProductionIdentity;
import dev.mesbase.business.production.domain.WorkorderEntity;
import dev.mesbase.business.production.domain.MesProductionReportEntity;
import dev.mesbase.business.production.domain.ProductionReportSubmitModels.Command;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.nio.file.Files;
import java.util.List;
import java.util.Set;
import java.util.Map;
import java.util.HashMap;
import java.util.UUID;
import java.util.ArrayList;
import java.util.Optional;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.MapPropertySource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.method.HandlerMethod;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.ArgumentMatchers.any;

/**
 * TEST ONLY: two physical H2 business databases + real center metadata, Spring transactions and production routing.
 */
class OperatorAllQualificationTest {
    @Configuration
    @EnableTransactionManagement(proxyTargetClass = true)
    static class Transactions {
    }

    AnnotationConfigApplicationContext context;
    RuntimeRegistry registry;
    OwnedTransactionManager manager;
    Map<String, JdbcTemplate> databases = new HashMap<>();
    JdbcTemplate centerJdbc;
    MesOperatorQualificationServiceImpl settings;
    MesOperationUserServiceImpl selected;
    OperatorQualificationRules rules;
    OperatorCandidateService candidates;
    ReportOperatorEligibilityService eligibility;

    @BeforeEach
    void prepare() throws Exception {
        var root = Path.of("").toAbsolutePath();
        if (!Files.exists(root.resolve("mes-admin"))) root = root.getParent();
        String baseline = Files.readString(root.resolve("mes-admin/src/main/resources/db/business/V1__init.sql"));
        String migration = Files.readString(root.resolve("mes-admin/src/main/resources/db/business/V89__operator_all_qualification.sql"));
        var mappers = Set.<Class<?>>of(MesOperatorQualificationMapper.class, MesOperationUserMapper.class,
                OperatorQualificationExtraMapper.class, OperationMapper.class, MesExecutionAssignmentMapper.class);
        registry = new RuntimeRegistry();
        var bindings = new HashMap<FactoryId, BusinessRuntimeBinding>();
        for (String factory : List.of("A", "B")) {
            var source = new DriverManagerDataSource("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE", "sa", "");
            var jdbc = new JdbcTemplate(source);
            for (String table : List.of("operation", "mes_operation_user", "mes_execution_assignment")) {
                String ddl = baseline.substring(baseline.indexOf("CREATE TABLE `" + table + "`"));
                jdbc.execute(ddl.substring(0, ddl.indexOf(") ENGINE=")) + ")");
            }
            jdbc.execute(migration.substring(0, migration.indexOf(") ENGINE=")) + ")");
            jdbc.update("INSERT INTO operation(id,code,name,status) VALUES(10,'A','工序A','ENABLED'),(20,'B','工序B','ENABLED'),(30,'OFF','停用工序','DISABLED')");
            jdbc.update("INSERT INTO mes_operation_user(operation_id,user_id,remark,create_by,create_time) VALUES(10,100,'必须保留','77',CURRENT_TIMESTAMP)");
            databases.put(factory, jdbc);
            var id = new FactoryId(factory);
            var datasource = new DatasourceIdentity("db_" + factory);
            registry.registerDedicated(id, datasource, 1, source, () -> {
            }, mappers);
            bindings.put(id, new BusinessRuntimeBinding(id, datasource, 1));
        }
        var source = new FactoryBusinessDataSource(scope -> bindings.get(scope.factoryId()), registry);
        manager = new OwnedTransactionManager(source);
        var session = new SqlSessionTemplate(PersistenceFactories.create(source, mappers, PersistenceFactories::pagination, manager::verifyAccess));
        var centerSource = new DriverManagerDataSource("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        centerJdbc = new JdbcTemplate(centerSource);
        centerJdbc.execute("CREATE TABLE sys_menu(menu_id BIGINT PRIMARY KEY,menu_name VARCHAR(80),parent_id BIGINT,order_num INT,path VARCHAR(100),component VARCHAR(100),`query` VARCHAR(100),is_frame VARCHAR(1),is_cache VARCHAR(1),menu_type VARCHAR(1),visible VARCHAR(1),status VARCHAR(1),perms VARCHAR(100),icon VARCHAR(100),create_time TIMESTAMP)");
        centerJdbc.execute("CREATE TABLE sys_user(user_id BIGINT PRIMARY KEY,user_name VARCHAR(80),nick_name VARCHAR(80),status VARCHAR(1),del_flag VARCHAR(1))");
        centerJdbc.execute("CREATE TABLE sys_role(role_id BIGINT PRIMARY KEY,kind VARCHAR(16),factory_id VARCHAR(64),status VARCHAR(1),del_flag VARCHAR(1))");
        centerJdbc.execute("CREATE TABLE sys_user_role(user_id BIGINT,role_id BIGINT)");
        centerJdbc.execute("CREATE TABLE sys_role_menu(role_id BIGINT,menu_id BIGINT)");
        centerJdbc.update("INSERT INTO sys_menu(menu_id,menu_name,parent_id,path,menu_type,status,perms) VALUES(1,'Business',0,'production','M','0',NULL),(2,'Read',1,'#','F','0','production:read')");
        centerJdbc.update("INSERT INTO sys_role VALUES(1,'PLATFORM',NULL,'0','0')");
        centerJdbc.update("INSERT INTO sys_role_menu VALUES(1,2)");
        for (int id = 1; id <= 250; id++) {
            centerJdbc.update("INSERT INTO sys_user VALUES(?,?,?,'0','0')", id, "user" + id, "人员" + id);
            centerJdbc.update("INSERT INTO sys_user_role VALUES(?,1)", id);
        }
        var center = new PlatformPersistence(centerSource, Set.of(SysMenuMapper.class, OperationUserDirectoryMapper.class), c -> {
            c.getTypeAliasRegistry().registerAlias("SysMenu", SysMenu.class);
            PersistenceFactories.pagination(c);
        });
        var users = new OperationUserDirectory(center, center.mapper(SysMenuMapper.class), center.mapper(OperationUserDirectoryMapper.class));
        var executions = mock(MesProductionExecutionMapper.class);
        var nodes = mock(RoutingNodeMapper.class);
        var execution = new MesProductionExecutionEntity();
        execution.setExecutionCode("code");
        execution.setRoutingId(1L);
        when(executions.selectByCode("code")).thenReturn(execution);
        var node = new RoutingNodeEntity();
        node.setId(10L);
        node.setRoutingId(1L);
        node.setOperationId(10L);
        when(nodes.selectByPrimaryKey(10L)).thenReturn(node);
        context = new AnnotationConfigApplicationContext();
        context.getEnvironment().getPropertySources()
                .addFirst(new MapPropertySource("TEST ONLY", Map.of("mes.business.enabled", "true")));
        context.register(Transactions.class);
        context.registerBean("businessTransactionManager", OwnedTransactionManager.class, () -> manager);
        for (var mapper : mappers) registerMapper(mapper, session);
        context.registerBean(OperationUserDirectory.class, () -> users);
        context.registerBean(MesProductionExecutionMapper.class, () -> executions);
        context.registerBean(RoutingNodeMapper.class, () -> nodes);
        context.registerBean(ProductionChainLocks.class, () -> mock(ProductionChainLocks.class));
        context.register(OperatorQualificationRules.class, MesOperatorQualificationServiceImpl.class,
                MesOperationUserServiceImpl.class, OperatorCandidateService.class, ReportOperatorEligibilityService.class);
        context.refresh();
        settings = context.getBean(MesOperatorQualificationServiceImpl.class);
        selected = context.getBean(MesOperationUserServiceImpl.class);
        rules = context.getBean(OperatorQualificationRules.class);
        candidates = context.getBean(OperatorCandidateService.class);
        eligibility = context.getBean(ReportOperatorEligibilityService.class);
        login();
        FactoryContext.bind(new FactoryScope(new FactoryId("A")));
    }

    private <T> void registerMapper(Class<T> type, SqlSessionTemplate session) {
        context.registerBean(type, () -> session.getMapper(type));
    }

    private void login() {
        var user = new SysUser();
        user.setUserId(77L);
        user.setUserName("manager");
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken(new LoginUser(77L, 1L, user, Set.of()), null, List.of()));
    }

    private void capability(Long operation, Long user) {
        new TransactionTemplate(manager).executeWithoutResult(status -> rules.requireCapability(operation, user));
    }

    @AfterEach
    void close() {
        FactoryContext.clear();
        SecurityContextHolder.clearContext();
        if (context != null) context.close();
        if (registry != null) registry.close();
    }

    @Test
    void allIsDynamicAndClosingPreservesExactSelectedFactsAndAudit() {
        var original = databases.get("A").queryForMap("SELECT * FROM mes_operation_user WHERE user_id=100");
        capability(10L, 100L);
        assertThrows(FoundationException.class, () -> capability(20L, 100L));
        settings.grantAll(100L, 0);
        capability(20L, 100L);
        databases.get("A").update("INSERT INTO operation(id,code,name,status) VALUES(40,'NEW','新增工序','ENABLED')");
        capability(40L, 100L);
        assertEquals(77L, databases.get("A")
                .queryForObject("SELECT create_by FROM mes_operator_qualification", Long.class));
        settings.revokeAll(100L, 1);
        capability(10L, 100L);
        assertThrows(FoundationException.class, () -> capability(20L, 100L));
        assertEquals(original, databases.get("A").queryForMap("SELECT * FROM mes_operation_user WHERE user_id=100"));
        assertEquals(2, settings.summary(100L, 0, 20).revision());
        assertEquals(1, settings.summary(100L, 0, 20).enabledExplicitTotal());
        assertFalse(settings.summary(100L, 0, 20).allOperations());
    }

    @Test
    void batchDisplayAndSubmitAgreeAndInvalidIdentitiesNeverGainCapabilities() {
        settings.grantAll(100L, 0);
        new TransactionTemplate(manager).executeWithoutResult(status -> {
            var failures = rules.capabilityFailures(List.of(10L, 20L, 30L, 999L), 100L);
            assertEquals(Set.of(30L, 999L), failures.keySet());
            for (Long op : List.of(10L, 20L)) rules.requireCapability(op, 100L);
            assertThrows(FoundationException.class, () -> rules.requireCapability(30L, 100L));
        });
        centerJdbc.update("UPDATE sys_user SET status='1' WHERE user_id=100");
        assertThrows(FoundationException.class, () -> capability(20L, 100L));
        assertThrows(FoundationException.class, () -> settings.grantAll(100L, 1));
        settings.revokeAll(100L, 1);
        assertFalse(settings.summary(100L, 0, 20).validUser());
        centerJdbc.update("DELETE FROM sys_user_role WHERE user_id=101");
        assertThrows(FoundationException.class, () -> settings.grantAll(101L, 0));
        assertThrows(FoundationException.class, () -> capability(20L, 101L));
    }

    @Test
    void validCandidateUnionDeduplicatesBeforePagingAndCountsAfterCenterFiltering() {
        var jdbc = databases.get("A");
        for (int id = 1; id <= 250; id++) {
            jdbc.update("INSERT INTO mes_operator_qualification VALUES(?,TRUE,1,77,CURRENT_TIMESTAMP,77,CURRENT_TIMESTAMP)", id);
            if (id % 2 == 0 && id != 100)
                jdbc.update("INSERT INTO mes_operation_user(operation_id,user_id) VALUES(10,?)", id);
        }
        centerJdbc.update("UPDATE sys_user SET status='1' WHERE MOD(user_id,5)=0");
        var first = candidates.candidates(10L, 0);
        var second = candidates.candidates(10L, 100);
        assertEquals(200, first.total());
        assertEquals(100, first.items().size());
        assertTrue(first.hasMore());
        assertEquals(200, second.total());
        assertEquals(100, second.items().size());
        assertFalse(second.hasMore());
        var ids = new ArrayList<Long>();
        first.items().forEach(u -> ids.add(u.id()));
        second.items().forEach(u -> ids.add(u.id()));
        assertEquals(200, Set.copyOf(ids).size());
        assertEquals(249L, ids.getFirst());
        assertEquals(1L, ids.getLast());
        assertTrue(candidates.candidates(10L, 200).items().isEmpty());
        assertThrows(FoundationException.class, () -> candidates.candidates(30L, 0));
    }

    @Test
    void allDoesNotBypassAssignmentOrActualPathAndQuantityChecks() {
        settings.grantAll(100L, 0);
        eligibility.requireEligible("code", 10L, 100L);
        databases.get("A")
                .update("INSERT INTO mes_execution_assignment(execution_code,routing_node_id,user_id,assigned_by,assigned_at) VALUES('code',10,101,77,CURRENT_TIMESTAMP)");
        assertEquals(ErrorCode.OPERATOR_NOT_ASSIGNED, assertThrows(FoundationException.class, () -> eligibility.requireEligible("code", 10L, 100L)).code());
        var zero = BigDecimal.ZERO;
        var node = new NodeQuantity("code", 10L, 10L, "A", "A", 1, zero, zero, zero, zero, zero, true);
        var view = new ExecutionQuantity("code", 1L, 1L, BigDecimal.TEN, "LOCKED", true, null, List.of(node), "path", BigDecimal.TEN, BigDecimal.TEN, zero, 1L);
        assertEquals(ErrorCode.REPORT_PATH_REQUIRED, assertThrows(FoundationException.class, () -> ProductionQuantityService.validateChange(view, 20L, BigDecimal.ONE)).code());
        assertThrows(FoundationException.class, () -> ProductionQuantityService.validateChange(view, 10L, new BigDecimal("11")));
    }

    @Test
    void currentFactoryRoutesSameUserToSeparateSettingsAndMissingScopeFails() {
        settings.grantAll(100L, 0);
        FactoryContext.bind(new FactoryScope(new FactoryId("B")));
        assertFalse(settings.summary(100L, 0, 20).allOperations());
        assertThrows(FoundationException.class, () -> capability(20L, 100L));
        FactoryContext.bind(new FactoryScope(new FactoryId("A")));
        capability(20L, 100L);
        FactoryContext.clear();
        assertThrows(FoundationException.class, () -> settings.summary(100L, 0, 20));
    }

    @Test
    void sameRevisionWritersAndFirstCreatorsHaveExactlyOneWinner() throws Exception {
        compete(0, true, true);
        assertEquals(1, settings.summary(100L, 0, 20).revision());
        compete(1, true, false);
        assertEquals(2, settings.summary(100L, 0, 20).revision());
        assertThrows(FoundationException.class, () -> settings.grantAll(100L, 1));
        assertEquals(1, databases.get("A")
                .queryForObject("SELECT COUNT(*) FROM mes_operator_qualification", Integer.class));
    }

    private void compete(int revision, boolean first, boolean second) throws Exception {
        var barrier = new CyclicBarrier(2);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var tasks = List.of(first, second).stream().map(all -> pool.submit(() -> {
                login();
                FactoryContext.bind(new FactoryScope(new FactoryId("A")));
                try {
                    barrier.await();
                    if (all) settings.grantAll(100L, revision);
                    else settings.revokeAll(100L, revision);
                    return true;
                } catch (FoundationException conflict) {
                    assertEquals(ErrorCode.RESOURCE_CONFLICT, conflict.code());
                    return false;
                } finally {
                    FactoryContext.clear();
                    SecurityContextHolder.clearContext();
                }
            })).toList();
            assertEquals(1, (tasks.get(0).get() ? 1 : 0) + (tasks.get(1).get() ? 1 : 0));
        }
    }

    @Test
    void explicitBatchManagementStillWorksIndependentlyOfAll() {
        settings.grantAll(100L, 0);
        selected.bindUsers(20L, List.of(100L, 101L));
        assertThrows(FoundationException.class, () -> selected.bindUsers(20L, List.of(101L, 102L)));
        assertEquals(2, databases.get("A")
                .queryForObject("SELECT COUNT(*) FROM mes_operation_user WHERE operation_id=20", Integer.class));
        var ids = databases.get("A")
                .queryForList("SELECT id FROM mes_operation_user WHERE operation_id=20", Long.class);
        selected.deleteByIds(ids);
        assertTrue(settings.summary(100L, 0, 20).allOperations());
        capability(20L, 100L);
    }

    @Test
    void reportingContextAndFormalSubmitUseTheSameAllQualification() {
        settings.grantAll(77L, 0);
        var identities = mock(ProductionIdentityService.class);
        var quantities = mock(ProductionQuantityService.class);
        var paths = mock(ExecutionPathService.class);
        var reports = mock(MesProductionReportMapper.class);
        var executions = context.getBean(MesProductionExecutionMapper.class);
        var orders = mock(WorkorderMapper.class);
        var locks = context.getBean(ProductionChainLocks.class);
        var execution = executions.selectByCode("code");
        execution.setStatus("READY");
        execution.setProductId(1L);
        var order = new WorkorderEntity();
        order.setStatus("RELEASED");
        when(orders.selectByPrimaryKey(1L)).thenReturn(order);
        when(identities.read("code")).thenReturn(new ProductionIdentity("code", "code", 1L, "WO", 1L, "P", "P", 1L, "R", 1, "READY", BigDecimal.TEN, "LOT"));
        var zero = BigDecimal.ZERO;
        var node = new NodeQuantity("code", 10L, 10L, "A", "A", 1, BigDecimal.TEN, zero, zero, zero, BigDecimal.TEN, true);
        var view = new ExecutionQuantity("code", 1L, 1L, BigDecimal.TEN, "LOCKED", true, null, List.of(node), "path", BigDecimal.TEN, BigDecimal.TEN, zero, 1L);
        when(quantities.getExecutionQuantities("code")).thenReturn(view);
        var locked = mock(ProductionChainLocks.LockedExecution.class);
        when(locked.execution()).thenReturn(execution);
        when(locks.lockedExecution("code", manager)).thenReturn(locked);
        when(reports.insert(any())).thenAnswer(call -> {
            MesProductionReportEntity row = call.getArgument(0);
            row.setId(1L);
            return 1;
        });
        when(reports.selectByPrimaryKey(1L)).thenReturn(new MesProductionReportEntity());
        var service = new ProductionReportSubmitService(identities, quantities, paths, eligibility, locks, reports,
                executions, orders, mock(WorkstationMapper.class), mock(BusinessSourceRules.class), manager, mock(MaterialQuantityRules.class));
        var command = new Command("code", 10L, BigDecimal.ONE, null, UUID.randomUUID().toString());
        new TransactionTemplate(manager).executeWithoutResult(status -> {
            assertEquals("AVAILABLE", service.context("code").candidates().getFirst().status());
            service.submit(command);
        });
        settings.revokeAll(77L, 1);
        new TransactionTemplate(manager).executeWithoutResult(status -> {
            assertEquals("NO_CAPABILITY", service.context("code").candidates().getFirst().status());
            assertEquals(ErrorCode.OPERATOR_CAPABILITY_REQUIRED, assertThrows(FoundationException.class, () -> service.submit(command)).code());
            status.setRollbackOnly();
        });
        execution.setStatus("FULLY_RECEIVED");
        assertThrows(FoundationException.class, () -> service.submit(command));
    }

    @Test
    void httpAuthorizationRemainsRequiredEvenWithAll() throws Exception {
        settings.grantAll(100L, 0);
        var authorization = mock(AuthorizationService.class);
        var selection = mock(FactorySelectionService.class);
        when(selection.resolve(any(), any())).thenReturn(Optional.of(new FactoryScope(new FactoryId("A"))));
        var interceptor = new FoundationAccessInterceptor(selection, authorization);
        var submit = mock(ProductionReportSubmitService.class);
        var reportController = new ProductionReportingController(submit);
        var method = new HandlerMethod(reportController, ProductionReportingController.class.getMethod("submit", ProductionReportingController.SubmitRequest.class));
        var request = new MockHttpServletRequest("POST", "/api/production/reporting");
        assertEquals(ErrorCode.FORBIDDEN, assertThrows(FoundationException.class, () -> interceptor.preHandle(request, new MockHttpServletResponse(), method)).code());
        verifyNoInteractions(submit);
        var controller = new MesOperatorQualificationController(settings);
        var grant = new HandlerMethod(controller, MesOperatorQualificationController.class.getMethod("grant", Long.class, MesOperatorQualificationController.Change.class));
        assertThrows(FoundationException.class, () -> interceptor.preHandle(new MockHttpServletRequest("POST", "/api/production/operator-qualifications/100/all"), new MockHttpServletResponse(), grant));
        var result = controller.summary(100L, 0, 20);
        assertEquals("100", result.userId());
        assertEquals("10", result.operations().getFirst().operationId());
    }
}
