package dev.mesbase.business.production;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import dev.mesbase.business.basic.dao.*;
import dev.mesbase.business.basic.domain.*;
import dev.mesbase.business.basic.service.MaterialBomService;
import dev.mesbase.business.directory.service.BusinessDirectoryService;
import dev.mesbase.business.integration.warehouse.ErpActionResult.Mode;
import dev.mesbase.business.integration.warehouse.WarehouseIntegration;
import dev.mesbase.business.production.dao.*;
import dev.mesbase.business.production.domain.*;
import dev.mesbase.business.production.service.*;
import dev.mesbase.business.wm.dao.*;
import dev.mesbase.business.wm.service.StockReferenceRules;
import dev.mesbase.common.core.domain.model.LoginUser;
import dev.mesbase.common.utils.SecurityUtils;
import dev.mesbase.core.factory.FactoryId;
import dev.mesbase.core.factory.FactoryScope;
import dev.mesbase.infrastructure.persistence.*;
import dev.mesbase.core.security.AuthorizationService;
import dev.mesbase.platform.execution.FactoryContext;
import dev.mesbase.framework.sync.SyncCoordinator;
import dev.mesbase.system.service.AutoCodeNumbering;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.apache.ibatis.executor.statement.StatementHandler;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.plugin.*;
import org.apache.ibatis.reflection.SystemMetaObject;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.sql.Connection;
import java.sql.Statement;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Persisted BOM fixtures with production mapper XML and the existing create/use-material services.
 */
class WorkOrderBomBuildDatabaseIntegrationTest {
    private DriverManagerDataSource dataSource;
    private JdbcTemplate jdbc;
    private SqlSessionTemplate sql;
    private MaterialBomMapper boms;
    private MaterialBomItemMapper bomItems;
    private MaterialMapper materials;
    private UnitMeasureMapper units;
    private WorkorderMapper orders;
    private MesWorkorderMaterialMapper materialLines;
    private WorkOrderBomBuildService builder;
    private FactoryScope scope;
    private RuntimeRegistry registry;
    private OwnedTransactionManager transactions;
    private final SqlTrace trace = new SqlTrace();
    private MaterialBomService bomImplementation;

    @BeforeEach
    void setUp() throws Exception {
        dataSource = new DriverManagerDataSource("jdbc:h2:mem:bom_build_db_" + UUID.randomUUID()
                + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        jdbc = new JdbcTemplate(dataSource);
        schema();
        scope = new FactoryScope(new FactoryId("BOM_BUILD_H2"));
        var identity = new DatasourceIdentity("BOM_BUILD_H2");
        var binding = new BusinessRuntimeBinding(scope.factoryId(), identity, 1);
        var mappers = Set.<Class<?>>of(MaterialMapper.class, UnitMeasureMapper.class, MaterialBomMapper.class,
                MaterialBomItemMapper.class, WorkorderMapper.class, MesWorkorderMaterialMapper.class,
                RoutingMapper.class, WorkOrderExtraMapper.class);
        registry = new RuntimeRegistry();
        registry.registerDedicated(scope.factoryId(), identity, 1, dataSource, () -> {
        }, mappers);
        var routed = new FactoryBusinessDataSource(ignored -> binding, registry);
        transactions = new OwnedTransactionManager(routed);
        var factory = PersistenceFactories.create(routed, mappers, configuration -> {
            PersistenceFactories.pagination(configuration);
            configuration.addInterceptor(trace);
        }, transactions::verifyAccess);
        sql = new SqlSessionTemplate(factory);
        boms = sql.getMapper(MaterialBomMapper.class);
        bomItems = sql.getMapper(MaterialBomItemMapper.class);
        materials = sql.getMapper(MaterialMapper.class);
        units = sql.getMapper(UnitMeasureMapper.class);
        orders = sql.getMapper(WorkorderMapper.class);
        materialLines = sql.getMapper(MesWorkorderMaterialMapper.class);
        seedBom();
        builder = buildService();
        installIdentity();
    }

    @AfterEach
    void tearDown() throws Exception {
        SecurityContextHolder.clearContext();
        if (registry != null) registry.close();
        if (dataSource != null) dataSource.getConnection().close();
    }

    @Test
    void persistedThreeLevelBomPreviewsAndCreatesPathSpecificOrdersAndDirectMaterialRows() {
        var source = new WorkOrderBomBuildModels.PreviewRequest(1L, 1L, bd("2"));
        trace.phase = "preview";
        var preview = FactoryContext.withScope(scope, () -> builder.preview(source));
        trace.print("preview");
        assertTrue(preview.complete());
        assertEquals(5, preview.orders().size());
        assertEquals(List.of("ROOT", "/100", "/100/200", "/101", "/101/300"),
                preview.orders().stream().map(WorkOrderBomBuildModels.OrderPreview::path).toList());
        assertEquals("4", preview.orders().get(1).plannedQty().stripTrailingZeros().toPlainString());
        assertEquals("6", preview.orders().get(3).plannedQty().stripTrailingZeros().toPlainString());
        assertEquals("24.000000", preview.orders().get(2).materials().getFirst().quantity().toPlainString());
        assertEquals("18.000000", preview.orders().get(4).materials().getFirst().quantity().toPlainString());
        assertEquals(preview.orders().get(2).productId(), preview.orders().get(4).productId());

        var choices = preview.orders().stream().map(order -> new WorkOrderBomBuildModels.OrderChoice(order.path(),
                "RULE", null, null, null, "integration fixture")).toList();
        var confirmation = new WorkOrderBomBuildModels.Confirm("db-request-1", preview.fingerprint(), choices);
        clearInvocations(bomImplementation);
        trace.phase = "confirm";
        var created = FactoryContext.withScope(scope, () -> builder.confirm(source, confirmation));
        trace.print("confirm");
        verify(bomImplementation, times(1)).explode("ROOT", 1L, bd("2"), 0, 1);
        verify(bomImplementation, times(10)).direct(anyLong(), any(BigDecimal.class));
        assertEquals(5, created.orders().size());
        assertEquals(5, jdbc.queryForObject("SELECT COUNT(*) FROM workorder", Integer.class));
        assertEquals(7, jdbc.queryForObject("SELECT COUNT(*) FROM mes_workorder_material", Integer.class));

        var root = created.orders().getFirst().workorder();
        assertEquals("ROOT", root.getBomBuildPath());
        var child = created.orders().get(1).workorder();
        assertEquals(root.getId(), child.getBomBuildParentWorkorderId());
        assertEquals(100L, child.getBomBuildSourceLineId());
        assertEquals("/100", child.getBomBuildPath());
        var repeatedA = created.orders().get(2).workorder();
        var repeatedB = created.orders().get(4).workorder();
        assertEquals(repeatedA.getProductId(), repeatedB.getProductId());
        assertNotEquals(repeatedA.getId(), repeatedB.getId());
        assertEquals(created.orders().get(1).workorder().getId(), repeatedA.getBomBuildParentWorkorderId());
        assertEquals(created.orders().get(3).workorder().getId(), repeatedB.getBomBuildParentWorkorderId());

        var demands = jdbc.query("SELECT w.bom_build_path,m.line_no,m.bom_item_id,m.material_id,m.total_required_qty "
                + "FROM mes_workorder_material m JOIN workorder w ON w.id=m.workorder_id "
                + "ORDER BY w.bom_build_path,m.line_no", (rs, row) -> List.of(rs.getString(1), rs.getInt(2),
                rs.getLong(3), rs.getLong(4), rs.getBigDecimal(5).stripTrailingZeros().toPlainString()));
        assertEquals(7, demands.size());
        assertTrue(demands.contains(List.of("ROOT", 1, 100L, 2L, "4")));
        assertTrue(demands.contains(List.of("/100/200", 1, 400L, 7L, "24")));
        assertTrue(demands.contains(List.of("/101/300", 1, 400L, 7L, "18")));

        trace.phase = "replay";
        var replay = FactoryContext.withScope(scope, () -> builder.confirm(source, confirmation));
        trace.print("replay");
        assertEquals(created.orders().stream().map(row -> row.workorder().getId()).toList(),
                replay.orders().stream().map(row -> row.workorder().getId()).toList());
        assertEquals(1, trace.writeConnections.size(), "all batch writes use one pinned connection");
        assertEquals(5, jdbc.queryForObject("SELECT COUNT(*) FROM workorder", Integer.class));
        assertEquals(7, jdbc.queryForObject("SELECT COUNT(*) FROM mes_workorder_material", Integer.class));
    }

    @Test
    void secondOrderMaterialSqlFailureRollsBackHeadersDemandsAndParentsAndCanRetrySameConfirmation() {
        var source = new WorkOrderBomBuildModels.PreviewRequest(1L, 1L, bd("2"));
        var preview = FactoryContext.withScope(scope, () -> builder.preview(source));
        var choices = preview.orders().stream().map(order -> new WorkOrderBomBuildModels.OrderChoice(order.path(),
                "RULE", null, null, null, "rollback fixture")).toList();
        var command = new WorkOrderBomBuildModels.Confirm("db-request-rollback", preview.fingerprint(), choices);
        // The root's three materials are legal; the second order's material violates this test-only SQL constraint.
        jdbc.execute("ALTER TABLE mes_workorder_material ADD CONSTRAINT fail_second_order CHECK(material_id<>4)");
        trace.completedWrites.clear();
        trace.writeConnections.clear();
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class,
                () -> FactoryContext.withScope(scope, () -> builder.confirm(source, command)));
        assertEquals(2L, trace.completedWrites.stream().filter(id -> id.endsWith("WorkorderMapper.insert")).count());
        assertEquals(3L, trace.completedWrites.stream().filter(id -> id.endsWith("MesWorkorderMaterialMapper.insert"))
                .count());
        assertEquals(1, trace.writeConnections.size());
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM workorder", Integer.class));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM mes_workorder_material", Integer.class));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM workorder WHERE bom_build_parent_workorder_id IS NOT NULL", Integer.class));
        jdbc.execute("ALTER TABLE mes_workorder_material DROP CONSTRAINT fail_second_order");
        var created = FactoryContext.withScope(scope, () -> builder.confirm(source, command));
        var replay = FactoryContext.withScope(scope, () -> builder.confirm(source, command));
        assertEquals(created.buildId(), replay.buildId());
        assertEquals(created.orders().stream().map(row -> row.workorder().getId()).toList(),
                replay.orders().stream().map(row -> row.workorder().getId()).toList());
        assertEquals(5, jdbc.queryForObject("SELECT COUNT(*) FROM workorder", Integer.class));
        assertEquals(7, jdbc.queryForObject("SELECT COUNT(*) FROM mes_workorder_material", Integer.class));
    }

    @Test
    void factoryAndOwnedTransactionGuardsRemainRequired() {
        assertThrows(RuntimeException.class, () -> FactoryContext.withScope(scope, () -> materials.selectByPrimaryKey(1L)));
        assertThrows(dev.mesbase.core.error.FoundationException.class,
                () -> builder.preview(new WorkOrderBomBuildModels.PreviewRequest(1L, 1L, bd("2"))));
    }

    @Test
    void changedBomRevisionRejectsConfirmationAgainstPersistedPreview() {
        var source = new WorkOrderBomBuildModels.PreviewRequest(1L, 1L, bd("2"));
        var preview = FactoryContext.withScope(scope, () -> builder.preview(source));
        var choices = preview.orders().stream().map(order -> new WorkOrderBomBuildModels.OrderChoice(order.path(),
                "RULE", null, null, null, "")).toList();
        jdbc.update("UPDATE material_bom SET revision=revision+1 WHERE id=1");
        var error = assertThrows(dev.mesbase.core.error.FoundationException.class, () ->
                FactoryContext.withScope(scope, () -> builder.confirm(source,
                        new WorkOrderBomBuildModels.Confirm("db-request-stale", preview.fingerprint(), choices))));
        assertEquals(dev.mesbase.core.error.ErrorCode.PRODUCTION_INPUT_STALE, error.code());
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM workorder", Integer.class));
    }

    private WorkOrderBomBuildService buildService() {
        bomImplementation = spy(new MaterialBomService(boms, bomItems, mock(MaterialBomCopyMapper.class), materials, units));
        var bomService = managed(bomImplementation);
        var sources = mock(BusinessSourceRules.class);
        when(sources.order(anyLong())).thenAnswer(call -> orders.selectByPrimaryKey(call.getArgument(0)));
        var integration = mock(WarehouseIntegration.class);
        when(integration.configuredMode(any())).thenReturn(Mode.LOCAL_ONLY);
        var quotas = mock(ProductionInputExtraMapper.class);
        when(quotas.hasFormalInput(anyLong())).thenReturn(false);
        var quantities = new MaterialQuantityRules(materials, units, mock(MesProductionExecutionMapper.class));
        var materialService = managed(new WorkorderMaterialService(materialLines, orders, materials, units,
                mock(WmWarehouseMapper.class), mock(ProductionChainLocks.class), integration,
                mock(StockReferenceRules.class), sources, boms, bomService, quotas,
                quantities, mock(AuthorizationService.class)));
        var queries = sql.getMapper(WorkOrderExtraMapper.class);
        var codes = mock(AutoCodeNumbering.class);
        var sequence = new AtomicInteger();
        when(codes.getDocNo("RULE", "WORK_ORDER")).thenAnswer(call -> {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive(), "numbering is outside business ownership");
            return "WO-" + sequence.incrementAndGet();
        });
        when(codes.rules("WORK_ORDER")).thenAnswer(call -> {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive(), "rule metadata is outside business ownership");
            return List.of(new AutoCodeNumbering.RuleOption("RULE", "工单", "WO"));
        });
        var workOrders = managed(new WorkOrderService(orders, mock(RoutingMapper.class), mock(RoutingProductMapper.class),
                mock(RoutingNodeMapper.class), mock(RoutingEdgeMapper.class), materials,
                mock(RoutingReferenceRules.class), mock(WmStockDocumentMapper.class), queries,
                mock(MesProductionExecutionMapper.class), mock(WorkorderErpFacts.class),
                mock(BusinessDirectoryService.class), materialLines, sources, mock(WorkorderErpExtraMapper.class),
                mock(SyncCoordinator.class), quantities, codes, materialService, transactions));
        return managed(new WorkOrderBomBuildService(bomService, boms, materials, workOrders, codes, transactions));
    }

    @SuppressWarnings("unchecked")
    private <T> T managed(T target) {
        var interceptor = new TransactionInterceptor();
        interceptor.setTransactionManager(transactions);
        interceptor.setTransactionAttributeSource(new AnnotationTransactionAttributeSource());
        var proxy = new ProxyFactory(target);
        proxy.setProxyTargetClass(true);
        proxy.addAdvice(interceptor);
        return (T) proxy.getProxy();
    }

    @Intercepts({
            @Signature(type = StatementHandler.class, method = "prepare", args = {Connection.class, Integer.class}),
            @Signature(type = StatementHandler.class, method = "update", args = {Statement.class})
    })
    public static class SqlTrace implements Interceptor {
        String phase = "setup";
        final Map<String, Map<String, Integer>> counts = new LinkedHashMap<>();
        final List<String> completedWrites = new ArrayList<>();
        final Set<Connection> writeConnections = Collections.newSetFromMap(new IdentityHashMap<>());

        public Object intercept(Invocation invocation) throws Throwable {
            var statement = (MappedStatement) SystemMetaObject.forObject(invocation.getTarget())
                    .getValue("delegate.mappedStatement");
            assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
            if ("prepare".equals(invocation.getMethod().getName())) {
                String key = phase + (TransactionSynchronizationManager.isCurrentTransactionReadOnly() ? ":read" : ":write");
                counts.computeIfAbsent(key, ignored -> new TreeMap<>()).merge(statement.getId(), 1, Integer::sum);
            } else {
                writeConnections.add(((Statement) invocation.getArgs()[0]).getConnection());
            }
            Object result = invocation.proceed();
            if ("update".equals(invocation.getMethod().getName())) completedWrites.add(statement.getId());
            return result;
        }

        void print(String selectedPhase) {
            counts.forEach((key, values) -> {
                if (key.startsWith(selectedPhase + ":"))
                    System.out.println("BOM_SQL " + key + " total=" + values.values().stream()
                            .mapToInt(Integer::intValue).sum() + " " + values);
            });
        }
    }

    private void seedBom() {
        jdbc.update("INSERT INTO unit_measure(id,code,name,symbol,status,must_be_whole_number,quantity_precision) "
                + "VALUES(1,'EA','件','件','ENABLED',1,0)");
        var rows = List.of(new Object[]{1L, "ROOT", "顶层"}, new Object[]{2L, "A", "组件A"},
                new Object[]{3L, "B", "组件B"}, new Object[]{4L, "SEMI", "共用半成品"},
                new Object[]{5L, "RAW-A", "原料A"}, new Object[]{6L, "RAW-B", "原料B"},
                new Object[]{7L, "RAW-S", "半成品原料"}, new Object[]{8L, "RAW-P", "同阶原料"});
        for (var row : rows)
            jdbc.update("INSERT INTO material(id,code,name,unit_id,status,work_order_rule_code) "
                    + "VALUES(?,?,?,1,'ENABLED','RULE')", row);
        jdbc.update("INSERT INTO material_bom(id,code,version,product_id,base_qty,unit_id,status,is_default,revision) "
                + "VALUES(1,'ROOT-BOM','V1',1,1,1,'PUBLISHED',1,7),(2,'A-BOM','V2',2,1,1,'PUBLISHED',0,11),"
                + "(3,'B-BOM','V3',3,1,1,'PUBLISHED',0,13),(4,'SEMI-BOM','V4',4,1,1,'PUBLISHED',0,17)");
        jdbc.update("INSERT INTO material_bom_item(id,bom_id,material_id,quantity,unit_id,child_bom_id,sort) VALUES "
                + "(100,1,2,2,1,2,1),(101,1,3,3,1,3,2),(102,1,8,0.5,1,NULL,3),"
                + "(200,2,4,2,1,4,1),(300,3,4,1,1,4,1),(400,4,7,3,1,NULL,1)");
    }

    private void schema() {
        jdbc.execute("CREATE TABLE routing (id BIGINT PRIMARY KEY,code VARCHAR(64),name VARCHAR(100),version VARCHAR(64),status VARCHAR(32),description VARCHAR(255),external_id VARCHAR(64),source_version VARCHAR(64),revision BIGINT,published_at TIMESTAMP)");
        jdbc.execute("CREATE TABLE routing_product (id BIGINT PRIMARY KEY,routing_id BIGINT,material_id BIGINT)");
        jdbc.execute("CREATE TABLE mes_production_execution (id BIGINT PRIMARY KEY,workorder_id BIGINT,status VARCHAR(32),target_qty DECIMAL(20,6))");
        jdbc.execute("CREATE TABLE material (id BIGINT PRIMARY KEY, code VARCHAR(64), name VARCHAR(255), "
                + "specification VARCHAR(500), category_id BIGINT, unit_id BIGINT, status VARCHAR(16), "
                + "external_id VARCHAR(128), external_code VARCHAR(128), work_order_rule_code VARCHAR(64))");
        jdbc.execute("CREATE TABLE unit_measure (id BIGINT PRIMARY KEY, code VARCHAR(64), name VARCHAR(255), "
                + "symbol VARCHAR(64), status VARCHAR(16), must_be_whole_number TINYINT, quantity_precision TINYINT)");
        jdbc.execute("CREATE TABLE material_bom (id BIGINT PRIMARY KEY, code VARCHAR(64), version VARCHAR(32), "
                + "product_id BIGINT, base_qty DECIMAL(20,6), unit_id BIGINT, status VARCHAR(16), is_default BOOLEAN, "
                + "revision BIGINT, remark VARCHAR(500), created_by BIGINT, created_at TIMESTAMP, published_at TIMESTAMP)");
        jdbc.execute("CREATE TABLE material_bom_item (id BIGINT PRIMARY KEY, bom_id BIGINT, material_id BIGINT, "
                + "quantity DECIMAL(20,6), unit_id BIGINT, child_bom_id BIGINT, sort INT)");
        jdbc.execute("CREATE TABLE workorder (id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY, "
                + "code VARCHAR(64), product_id BIGINT, planned_qty DECIMAL(20,6), routing_id BIGINT, "
                + "status VARCHAR(32), planned_start_at TIMESTAMP, planned_end_at TIMESTAMP, remark VARCHAR(500), "
                + "revision INT, create_time TIMESTAMP, update_time TIMESTAMP, source VARCHAR(32), bom_id BIGINT, "
                + "bom_revision BIGINT, material_revision BIGINT, material_warehouse_ids VARCHAR(2000), "
                + "material_work_center VARCHAR(100), material_status VARCHAR(32), material_frozen_at TIMESTAMP, "
                + "material_updated_by BIGINT, material_updated_at TIMESTAMP, bom_build_id VARCHAR(36), "
                + "bom_build_parent_workorder_id BIGINT, bom_build_source_line_id BIGINT, bom_build_path VARCHAR(2048), "
                + "bom_build_request_id VARCHAR(64) UNIQUE, bom_build_request_hash CHAR(64))");
        jdbc.execute("CREATE TABLE mes_workorder_material (id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY, "
                + "material_id BIGINT, unit_id BIGINT, total_required_qty DECIMAL(20,6), planned_qty DECIMAL(20,6), "
                + "warehouse_ids VARCHAR(2000), work_center VARCHAR(100), revision BIGINT, status VARCHAR(32), "
                + "updated_by BIGINT, updated_at TIMESTAMP, bom_id BIGINT, bom_base_qty DECIMAL(20,6), "
                + "bom_component_qty DECIMAL(20,6), output_unit_id BIGINT, frozen_at TIMESTAMP, workorder_id BIGINT, "
                + "line_no INT, bom_item_id BIGINT, manually_adjusted BOOLEAN)");
    }

    private void installIdentity() {
        var login = new LoginUser();
        login.setUserId(1L);
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken(login, "", List.of()));
    }

    private static BigDecimal bd(String value) {
        return new BigDecimal(value);
    }
}
