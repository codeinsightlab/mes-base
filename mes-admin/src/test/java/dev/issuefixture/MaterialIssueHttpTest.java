package dev.issuefixture;

import dev.mesbase.business.production.service.ProductionQuantityService;
import dev.mesbase.business.issue.service.MaterialIssueInputFacts;
import dev.mesbase.business.wm.dao.WmInventoryMapper;
import dev.mesbase.business.production.dao.MesProductionScanIdentityMapper;
import dev.mesbase.business.issue.dao.MesMaterialIssueInputMapper;
import dev.mesbase.business.production.dao.MesExecutionPathMapper;
import dev.mesbase.business.production.dao.MesExecutionAssignmentMapper;
import dev.mesbase.business.production.dao.MesExecutionPathNodeMapper;
import dev.mesbase.business.issue.dao.MesMaterialIssueSubmissionMapper;
import dev.mesbase.business.production.service.ProductionChainLocks;
import dev.mesbase.business.production.service.ExecutionPathService;
import dev.mesbase.business.production.controller.ExecutionActionController;
import dev.mesbase.business.issue.service.MaterialIssueSubmissionService;
import dev.mesbase.business.issue.controller.MaterialIssueSubmissionController;


import dev.mesbase.business.production.service.WorkorderErpFacts;
import dev.mesbase.business.production.dao.WorkorderErpExtraMapper;

import dev.mesbase.business.issue.service.MaterialIssueErpFacts;
import dev.mesbase.business.issue.service.MaterialIssueErpService;
import dev.mesbase.business.issue.controller.MaterialIssueErpController;
import dev.mesbase.business.issue.dao.MesMaterialIssueErpExtraMapper;

import dev.mesbase.business.integration.warehouse.WarehouseIntegration;
import dev.mesbase.business.issue.service.MaterialIssueVoidService;
import dev.mesbase.business.issue.controller.MaterialIssueVoidController;

import dev.mesbase.business.basic.dao.MaterialMapper;
import dev.mesbase.business.issue.dao.MesMaterialIssueMapper;
import dev.mesbase.business.issue.dao.MesMaterialIssueItemMapper;
import dev.mesbase.business.basic.dao.UnitMeasureMapper;
import dev.mesbase.business.production.dao.RoutingMapper;
import dev.mesbase.business.production.dao.RoutingProductMapper;
import dev.mesbase.business.production.dao.WorkorderMapper;
import dev.mesbase.business.production.dao.WorkOrderExtraMapper;
import dev.mesbase.business.production.dao.ProductionBatchMapper;
import dev.mesbase.business.production.dao.BatchRouteNodeMapper;
import dev.mesbase.business.production.dao.RoutingNodeMapper;
import dev.mesbase.business.production.dao.RoutingEdgeMapper;
import dev.mesbase.business.production.dao.OperationMapper;
import dev.mesbase.business.production.service.WorkOrderService;
import dev.mesbase.business.production.service.LegacyBatchHistoryQuery;
import dev.mesbase.business.production.controller.LegacyBatchHistoryController;
import dev.mesbase.business.production.service.RoutingReferenceRules;
import dev.mesbase.business.production.controller.WorkOrderController;
import dev.mesbase.business.production.dao.MesProductionExecutionMapper;
import dev.mesbase.business.production.service.ProductionExecutionRules;
import dev.mesbase.business.production.dao.MesProductionReportMapper;
import dev.mesbase.business.production.dao.MesProductionReceiptMapper;
import dev.mesbase.business.production.dao.ProductionReceiptQueryMapper;
import dev.mesbase.business.quality.dao.MesProductionDefectMapper;
import dev.mesbase.business.quality.service.ExecutionQualityReferenceQueryImpl;
import dev.mesbase.business.quality.service.QualityTreatmentService;
import dev.mesbase.business.quality.dao.MesQualityTreatmentMapper;
import dev.mesbase.business.production.controller.ProductionQuantityErrorAdvice;
import dev.mesbase.business.basic.dao.WorkstationMapper;
import dev.mesbase.business.production.dao.ProductionQuantityMapper;
import dev.mesbase.business.production.service.ProductionIdentityService;
import dev.mesbase.business.production.service.impl.MesProductionExecutionServiceImpl;
import dev.mesbase.business.production.controller.MesProductionExecutionController;
import dev.mesbase.business.production.service.ProductionExecutionQueryService;
import dev.mesbase.business.directory.service.BusinessDirectoryService;
import dev.mesbase.business.directory.domain.DirectoryLabel;
import dev.mesbase.business.production.domain.RoutingExample;
import dev.mesbase.business.production.domain.OperationExample;
import java.util.LinkedHashMap;
import java.util.Collection;
import dev.mesbase.business.production.controller.ProductionIdentityController;
import dev.mesbase.business.issue.controller.MesMaterialIssueController;
import dev.mesbase.business.issue.controller.MesMaterialIssueItemController;
import dev.mesbase.business.issue.service.impl.MesMaterialIssueServiceImpl;
import dev.mesbase.business.issue.service.impl.MesMaterialIssueItemServiceImpl;
import dev.mesbase.business.issue.service.MaterialIssueRules;

import dev.mesbase.business.wm.dao.WmWarehouseMapper;
import dev.mesbase.common.core.domain.entity.SysUser;
import dev.mesbase.common.core.domain.model.LoginUser;
import dev.mesbase.core.factory.Factory;
import dev.mesbase.core.factory.FactoryId;
import dev.mesbase.core.factory.FactoryStatus;
import dev.mesbase.core.factory.FactorySelectionService;
import dev.mesbase.framework.security.SystemTokenFilter;
import dev.mesbase.framework.web.service.TokenService;
import dev.mesbase.infrastructure.cache.BusinessCacheProcessor;
import dev.mesbase.infrastructure.catalog.InMemoryFactoryCatalog;
import dev.mesbase.infrastructure.catalog.InMemoryFactoryPreference;
import dev.mesbase.infrastructure.persistence.BusinessRuntimeBinding;
import dev.mesbase.infrastructure.persistence.ConfiguredBusinessPersistenceResolver;
import dev.mesbase.infrastructure.persistence.DatasourceIdentity;
import dev.mesbase.infrastructure.persistence.FactoryBusinessDataSource;
import dev.mesbase.infrastructure.persistence.OwnedTransactionManager;
import dev.mesbase.infrastructure.persistence.PersistenceFactories;
import dev.mesbase.infrastructure.persistence.RuntimeRegistry;
import dev.mesbase.platform.web.FoundationAccessInterceptor;
import dev.mesbase.platform.web.FoundationErrorAdvice;
import dev.mesbase.platform.web.RequestBoundaryFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.regex.Pattern;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.MapPropertySource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

/**
 * TEST ONLY: real generated SQL, HTTP authorization and transactions; two separate H2 databases.
 */
class MaterialIssueHttpTest {
    static final Set<Class<?>> MAPPERS = Set.of(MesExecutionAssignmentMapper.class, MesMaterialIssueInputMapper.class, MesProductionScanIdentityMapper.class, WmInventoryMapper.class, MesProductionReceiptMapper.class, ProductionReceiptQueryMapper.class, MesProductionDefectMapper.class, MesQualityTreatmentMapper.class, WorkstationMapper.class, MesProductionReportMapper.class, ProductionQuantityMapper.class, MesExecutionPathMapper.class, MesExecutionPathNodeMapper.class, MesMaterialIssueSubmissionMapper.class, WorkorderErpExtraMapper.class, MesMaterialIssueErpExtraMapper.class, WorkOrderExtraMapper.class, ProductionBatchMapper.class, BatchRouteNodeMapper.class, RoutingNodeMapper.class, RoutingEdgeMapper.class, OperationMapper.class, MesProductionExecutionMapper.class, MaterialMapper.class, UnitMeasureMapper.class, WmWarehouseMapper.class, RoutingMapper.class, RoutingProductMapper.class, WorkorderMapper.class, MesMaterialIssueMapper.class, MesMaterialIssueItemMapper.class);

    static class Fixture implements AutoCloseable {
        final RuntimeRegistry registry = new RuntimeRegistry();
        final Map<String, JdbcTemplate> business = new HashMap<>();
        final InMemoryFactoryCatalog catalog;
        final ConfiguredBusinessPersistenceResolver resolver;

        Fixture() throws Exception {
            catalog = new InMemoryFactoryCatalog(List.of(factory("A"), factory("B")));
            var bindings = new HashMap<FactoryId, BusinessRuntimeBinding>();
            for (String id : List.of("A", "B")) {
                var source = source();
                var jdbc = new JdbcTemplate(source);
                for (String name : List.of("V2__simple_business_capabilities.sql", "V3__operation_routing.sql", "V4__routing_operation_instruction_snapshot.sql", "V9__routing_graph.sql", "V8__warehouse_inventory.sql", "V11__material_issue.sql", "V12__material_issue_quantity_precision.sql", "V13__workorder_batch_route.sql", "V16__production_execution.sql", "V18__material_issue_void.sql", "V19__local_void_external_effect.sql", "V20__material_issue_erp_extra.sql", "V21__workorder_external_extra.sql", "V22__unverified_legacy_erp_provider.sql", "V23__production_core_v1.sql", "V24__operation_user_execution_assignment.sql", "V25__production_report_ledger.sql", "V26__production_defect.sql", "V27__production_report_request.sql", "V29__single_material_production_input.sql", "V32__quality_treatment_facts.sql", "V33__admin_report_command_fingerprint.sql")) {
                    String ddl = Files.readString(Path.of("mes-admin/src/main/resources/db/business/" + name))
                            .replace(") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT=", ") COMMENT ")
                            .replaceAll("(?m)^\\s*factory_id [^\\n]+\\n", "")
                            .replace("factory_id,", "")
                            .replace("factory_id, ", "")
                            .replace(" CHARACTER SET utf8mb4 COLLATE utf8mb4_bin", "");
                    if (name.equals("V18__material_issue_void.sql"))
                        ddl = ddl.replace(",\n ADD COLUMN", ";\nALTER TABLE mes_material_issue ADD COLUMN");
                    if (name.equals("V20__material_issue_erp_extra.sql"))
                        ddl = ddl.replace(",\n DROP COLUMN", ";\nALTER TABLE mes_material_issue DROP COLUMN");
                    if (name.equals("V21__workorder_external_extra.sql"))
                        ddl = ddl.replace(", DROP COLUMN", "; ALTER TABLE workorder DROP COLUMN");
                    if (name.equals("V23__production_core_v1.sql"))
                        ddl = ddl.replace(",\n MODIFY COLUMN", ";\nALTER TABLE mes_material_issue ALTER COLUMN");
                    if (name.equals("V25__production_report_ledger.sql")) ddl = ddl.split("-- 服务和数据库")[0];
                    if (name.equals("V26__production_defect.sql") || name.equals("V32__quality_treatment_facts.sql"))
                        ddl = ddl.split("CREATE TRIGGER")[0].replace(" COLLATE utf8mb4_bin", "")
                                .replace(" CHARACTER SET ascii COLLATE ascii_bin", "");
                    if (name.equals("V33__admin_report_command_fingerprint.sql"))
                        ddl = ddl.replace(" CHARACTER SET ascii COLLATE ascii_bin", "");
                    if (name.equals("V27__production_report_request.sql"))
                        ddl = ddl.replace(" CHARACTER SET ascii COLLATE ascii_bin", "")
                                .replace(",\n ADD UNIQUE KEY uk_report_request_id(request_id)", ";\nALTER TABLE mes_production_report ADD CONSTRAINT uk_report_request_id UNIQUE(request_id)");
                    if (name.equals("V29__single_material_production_input.sql")) {
                        ddl = ddl.split("-- 旧库存")[0].replace(" CHARACTER SET ascii COLLATE ascii_bin", "");
                        jdbc.execute("ALTER TABLE wm_inventory ADD batch_no VARCHAR(128)");
                        jdbc.execute("ALTER TABLE wm_inventory ADD bin_code VARCHAR(64)");
                        jdbc.execute("ALTER TABLE wm_inventory DROP CONSTRAINT uk_inventory_factory_material_warehouse");
                    }
                    for (String statement : ddl.split(";")) if (!statement.isBlank()) jdbc.execute(statement);
                }
                jdbc.execute("ALTER TABLE mes_production_scan_identity ADD product_code VARCHAR(64)");
                jdbc.execute("ALTER TABLE mes_production_scan_identity ADD material_code VARCHAR(64)");
                jdbc.execute("ALTER TABLE mes_production_scan_identity ADD material_lot_code VARCHAR(128)");
                jdbc.update("INSERT INTO material(id,code,name,unit_id,material_type,status) VALUES(1,'M1','物料一',1,'PRODUCT','ENABLED')");
                jdbc.update("INSERT INTO workorder(id,code,product_id,planned_qty,routing_id,status,revision,create_time,update_time) VALUES(1,'WO1',1,1000,1,'DRAFT',0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");
                jdbc.update("INSERT INTO unit_measure(id,code,name,status) VALUES(1,'KG','千克','ENABLED')");
                jdbc.update("INSERT INTO wm_warehouse(id,code,name,status) VALUES(1,'W1','验收仓库','ENABLED')");
                jdbc.update("INSERT INTO routing(id,code,name,version,status,revision,published_at) VALUES(1,'R1','已发布版本',1,'PUBLISHED',0,CURRENT_TIMESTAMP),(2,'R1','草稿版本',2,'DRAFT',0,NULL)");
                jdbc.update("INSERT INTO routing_product(routing_id,material_id,material_code,material_name) VALUES(1,1,'M1','产品')");
                jdbc.update("INSERT INTO workorder_erp_extra(id) VALUES(1)");
                business.put(id, jdbc);
                var identity = new DatasourceIdentity("TEST_ONLY_" + id);
                var factoryId = new FactoryId(id);
                registry.registerDedicated(factoryId, identity, 1, source, () -> {
                }, MAPPERS);
                bindings.put(factoryId, new BusinessRuntimeBinding(factoryId, identity, 1));
            }
            resolver = new ConfiguredBusinessPersistenceResolver(catalog, bindings);
        }

        static Factory factory(String id) {
            return new Factory(new FactoryId(id), "TEST_ONLY", id, id, FactoryStatus.ENABLED);
        }

        static DriverManagerDataSource source() {
            return new DriverManagerDataSource("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1", "sa", "");
        }

        public void close() {
            registry.close();
        }
    }

    @Configuration
    @EnableWebMvc
    @EnableWebSecurity
    @EnableTransactionManagement
    @Import({QualityTreatmentService.class, MaterialIssueInputFacts.class, ProductionQuantityService.class, ProductionQuantityErrorAdvice.class, ExecutionQualityReferenceQueryImpl.class, ProductionExecutionQueryService.class, ProductionChainLocks.class, ExecutionPathService.class, ExecutionActionController.class, MaterialIssueSubmissionService.class, MaterialIssueSubmissionController.class, WorkorderErpFacts.class, MaterialIssueErpFacts.class, MaterialIssueErpService.class, MaterialIssueErpController.class, WarehouseIntegration.class, MaterialIssueVoidService.class, MaterialIssueVoidController.class, MesMaterialIssueController.class, MesMaterialIssueItemController.class, MesMaterialIssueServiceImpl.class, MesMaterialIssueItemServiceImpl.class, MaterialIssueRules.class, ProductionExecutionRules.class, ProductionIdentityService.class, MesProductionExecutionServiceImpl.class, MesProductionExecutionController.class, ProductionIdentityController.class, WorkOrderController.class, WorkOrderService.class, LegacyBatchHistoryQuery.class, LegacyBatchHistoryController.class, RoutingReferenceRules.class, FoundationErrorAdvice.class})
    static class Web implements WebMvcConfigurer {
        @Bean
        BusinessDirectoryService directoryLabels(RoutingMapper routes, OperationMapper operations) {
            var directory = mock(BusinessDirectoryService.class);
            when(directory.userLabels(any())).thenReturn(Map.of(100L, new DirectoryLabel(100L, null, "历史操作人员", null), 1L, new DirectoryLabel(1L, null, "验收提交人", null)));
            when(directory.routingLabels(any())).thenAnswer(call -> {
                Collection<Long> ids = call.getArgument(0);
                var result = new LinkedHashMap<Long, DirectoryLabel>();
                if (!ids.isEmpty()) {
                    var filter = new RoutingExample();
                    filter.createCriteria().andIdIn(List.copyOf(ids));
                    for (var row : routes.selectByExample(filter))
                        result.put(row.getId(), new DirectoryLabel(row.getId(), row.getCode(), row.getName(), row.getVersion()));
                }
                return result;
            });
            when(directory.routingLabel(any())).thenAnswer(call -> {
                var row = routes.selectByPrimaryKey(call.getArgument(0));
                return row == null ? null : new DirectoryLabel(row.getId(), row.getCode(), row.getName(), row.getVersion());
            });
            when(directory.operationLabels(any())).thenAnswer(call -> {
                Collection<Long> ids = call.getArgument(0);
                var result = new LinkedHashMap<Long, DirectoryLabel>();
                if (!ids.isEmpty()) {
                    var filter = new OperationExample();
                    filter.createCriteria().andIdIn(List.copyOf(ids));
                    for (var row : operations.selectByExample(filter))
                        result.put(row.getId(), new DirectoryLabel(row.getId(), row.getCode(), row.getName(), null));
                }
                return result;
            });
            return directory;
        }

        @Bean(destroyMethod = "close")
        Fixture fixture() throws Exception {
            return new Fixture();
        }

        @Bean
        BusinessCacheProcessor caches() {
            return mock(BusinessCacheProcessor.class);
        }

        @Bean
        FactoryBusinessDataSource businessDataSource(Fixture f) {
            return new FactoryBusinessDataSource(f.resolver, f.registry);
        }

        @Bean(name = "businessTransactionManager")
        OwnedTransactionManager transactions(FactoryBusinessDataSource source) {
            return new OwnedTransactionManager(source);
        }

        @Bean
        SqlSessionFactory sessions(FactoryBusinessDataSource source, OwnedTransactionManager manager) {
            return PersistenceFactories.create(source, MAPPERS, PersistenceFactories::pagination, manager::verifyAccess);
        }

        @Bean
        MesExecutionAssignmentMapper assignmentMapper(SqlSessionFactory f) {
            return new SqlSessionTemplate(f).getMapper(MesExecutionAssignmentMapper.class);
        }

        @Bean
        MesExecutionPathMapper pathMapper(SqlSessionFactory f) {
            return new SqlSessionTemplate(f).getMapper(MesExecutionPathMapper.class);
        }

        @Bean
        MesExecutionPathNodeMapper pathNodeMapper(SqlSessionFactory f) {
            return new SqlSessionTemplate(f).getMapper(MesExecutionPathNodeMapper.class);
        }

        @Bean
        MesMaterialIssueInputMapper inputMapper(SqlSessionFactory f) {
            return new SqlSessionTemplate(f).getMapper(MesMaterialIssueInputMapper.class);
        }

        @Bean
        MesProductionScanIdentityMapper scanMapper(SqlSessionFactory f) {
            return new SqlSessionTemplate(f).getMapper(MesProductionScanIdentityMapper.class);
        }

        @Bean
        WmInventoryMapper inventoryMapper(SqlSessionFactory f) {
            return new SqlSessionTemplate(f).getMapper(WmInventoryMapper.class);
        }

        @Bean
        MesMaterialIssueSubmissionMapper submissionMapper(SqlSessionFactory f) {
            return new SqlSessionTemplate(f).getMapper(MesMaterialIssueSubmissionMapper.class);
        }

        @Bean
        WorkorderErpExtraMapper workorderErpMapper(SqlSessionFactory f) {
            return new SqlSessionTemplate(f).getMapper(WorkorderErpExtraMapper.class);
        }

        @Bean
        MesMaterialIssueErpExtraMapper extraMapper(SqlSessionFactory f) {
            return new SqlSessionTemplate(f).getMapper(MesMaterialIssueErpExtraMapper.class);
        }

        @Bean
        MesProductionExecutionMapper executionMapper(SqlSessionFactory f) {
            return new SqlSessionTemplate(f).getMapper(MesProductionExecutionMapper.class);
        }

        @Bean
        MesQualityTreatmentMapper qualityTreatments(SqlSessionFactory f) {
            return new SqlSessionTemplate(f).getMapper(MesQualityTreatmentMapper.class);
        }

        @Bean
        MesProductionDefectMapper qualityReferenceMapper(SqlSessionFactory f) {
            return new SqlSessionTemplate(f).getMapper(MesProductionDefectMapper.class);
        }

        @Bean
        MesProductionReportMapper reportMapper(SqlSessionFactory f) {
            return new SqlSessionTemplate(f).getMapper(MesProductionReportMapper.class);
        }

        @Bean
        ProductionQuantityMapper quantityMapper(SqlSessionFactory f) {
            return new SqlSessionTemplate(f).getMapper(ProductionQuantityMapper.class);
        }

        @Bean
        WorkOrderExtraMapper beanWorkOrderExtraMapper(SqlSessionFactory f) {
            return new SqlSessionTemplate(f).getMapper(WorkOrderExtraMapper.class);
        }

        @Bean
        ProductionBatchMapper beanProductionBatchMapper(SqlSessionFactory f) {
            return new SqlSessionTemplate(f).getMapper(ProductionBatchMapper.class);
        }

        @Bean
        BatchRouteNodeMapper beanBatchRouteNodeMapper(SqlSessionFactory f) {
            return new SqlSessionTemplate(f).getMapper(BatchRouteNodeMapper.class);
        }

        @Bean
        RoutingNodeMapper beanRoutingNodeMapper(SqlSessionFactory f) {
            return new SqlSessionTemplate(f).getMapper(RoutingNodeMapper.class);
        }

        @Bean
        RoutingEdgeMapper beanRoutingEdgeMapper(SqlSessionFactory f) {
            return new SqlSessionTemplate(f).getMapper(RoutingEdgeMapper.class);
        }

        @Bean
        OperationMapper beanOperationMapper(SqlSessionFactory f) {
            return new SqlSessionTemplate(f).getMapper(OperationMapper.class);
        }

        @Bean
        MaterialMapper materials(SqlSessionFactory f) {
            return new SqlSessionTemplate(f).getMapper(MaterialMapper.class);
        }

        @Bean
        WmWarehouseMapper warehouses(SqlSessionFactory f) {
            return new SqlSessionTemplate(f).getMapper(WmWarehouseMapper.class);
        }

        @Bean
        MesMaterialIssueMapper mapperMesMaterialIssueMapper(SqlSessionFactory f) {
            return new SqlSessionTemplate(f).getMapper(MesMaterialIssueMapper.class);
        }

        @Bean
        MesMaterialIssueItemMapper mapperMesMaterialIssueItemMapper(SqlSessionFactory f) {
            return new SqlSessionTemplate(f).getMapper(MesMaterialIssueItemMapper.class);
        }

        @Bean
        UnitMeasureMapper mapperUnitMeasureMapper(SqlSessionFactory f) {
            return new SqlSessionTemplate(f).getMapper(UnitMeasureMapper.class);
        }

        @Bean
        RoutingMapper mapperRoutingMapper(SqlSessionFactory f) {
            return new SqlSessionTemplate(f).getMapper(RoutingMapper.class);
        }

        @Bean
        RoutingProductMapper routingProducts(SqlSessionFactory f) {
            return new SqlSessionTemplate(f).getMapper(RoutingProductMapper.class);
        }

        @Bean
        WorkorderMapper mapperWorkorderMapper(SqlSessionFactory f) {
            return new SqlSessionTemplate(f).getMapper(WorkorderMapper.class);
        }

        @Bean
        SecurityFilterChain security(HttpSecurity http) throws Exception {
            var tokens = mock(TokenService.class);
            when(tokens.getLoginUser(any())).thenAnswer(call -> {
                String value = call.getArgument(0, HttpServletRequest.class).getHeader("Authorization");
                if (value == null) return null;
                var user = new SysUser();
                user.setUserId(1L);
                user.setUserName(value.endsWith("NONE") ? "none" : value.endsWith("CRUD") ? "crud" : "full");
                return new LoginUser(1L, 1L, user, Set.of());
            });
            return http.csrf(c -> c.disable())
                    .addFilterBefore(new SystemTokenFilter(tokens), UsernamePasswordAuthenticationFilter.class)
                    .authorizeHttpRequests(c -> c.anyRequest().authenticated()).build();
        }

        public void addInterceptors(InterceptorRegistry r) {
            try {
                var f = fixture();
                r.addInterceptor(new FoundationAccessInterceptor(new FactorySelectionService(f.catalog, new InMemoryFactoryPreference()),
                        (actor, scope, permission) -> "full".equals(actor.id()) || "crud".equals(actor.id()) && (permission.code()
                                .endsWith(":list") || permission.code().endsWith(":query") || permission.code()
                                .equals("business:quality:defect:read") || permission.code()
                                .equals("business:quality:defect:create"))));
            } catch (Exception failure) {
                throw new IllegalStateException(failure);
            }
        }
    }

    static AnnotationConfigWebApplicationContext context() {
        var c = new AnnotationConfigWebApplicationContext();
        c.setServletContext(new MockServletContext());
        c.getEnvironment().getPropertySources()
                .addFirst(new MapPropertySource("TEST ONLY", Map.of("mes.business.enabled", "true", "mes.integration.warehouse.factories.A.mode", "LOCAL_ONLY", "mes.integration.warehouse.factories.B.mode", "LOCAL_ONLY")));
        c.register(Web.class);
        c.refresh();
        return c;
    }

    static MockMvc mvc(AnnotationConfigWebApplicationContext c) {
        return MockMvcBuilders.webAppContextSetup(c)
                .addFilters(new RequestBoundaryFilter(), c.getBean(FilterChainProxy.class)).build();
    }

    static final String H = "/api/issue/mes-material-issue", I = H + "-item";
    static final String HEADER = "{\"issueNo\":\"QA_ISSUE\",\"workorderId\":\"1\",\"routingId\":\"1\",\"status\":\"RECORDED\"}";
    static final String ITEM = "{\"issueId\":\"1\",\"materialId\":\"1\",\"batchNo\":\"ERP/ arbitrary-batch\",\"quantity\":\"99999999999999.123456\",\"unitId\":\"1\",\"warehouseId\":\"1\"}";

    @Test
    void scopedCrudPrecisionSnapshotsAndParentProtection() throws Exception {
        try (var c = context()) {
            var m = mvc(c);
            m.perform(post(H).header("Authorization", "Bearer FULL").header("X-Factory-Id", "A")
                            .contentType("application/json").content(HEADER))
                    .andExpect(status().isOk()).andExpect(jsonPath("createBy").value("1"))
                    .andExpect(jsonPath("workorderCode").value("WO1")).andExpect(jsonPath("productCode").value("M1"));
            m.perform(post(I).header("Authorization", "Bearer FULL").header("X-Factory-Id", "A")
                            .contentType("application/json").content(ITEM))
                    .andExpect(status().isOk()).andExpect(jsonPath("quantity").value("99999999999999.123456"))
                    .andExpect(jsonPath("materialCode").value("M1")).andExpect(jsonPath("warehouseCode").value("W1"));
            // Same material and batch can occur repeatedly; no guessed uniqueness.
            m.perform(post(I).header("Authorization", "Bearer FULL").header("X-Factory-Id", "A")
                            .contentType("application/json").content(ITEM))
                    .andExpect(status().isOk());
            m.perform(get(I).header("Authorization", "Bearer FULL").header("X-Factory-Id", "A").param("issueId", "1")
                            .param("limit", "1"))
                    .andExpect(status().isOk()).andExpect(jsonPath("total").value(2))
                    .andExpect(jsonPath("items.length()").value(1));
            m.perform(get(H + "/1").header("Authorization", "Bearer FULL").header("X-Factory-Id", "B"))
                    .andExpect(status().isNotFound());
            // Identical local IDs are valid in separate databases without row factory fields.
            m.perform(post(H).header("Authorization", "Bearer FULL").header("X-Factory-Id", "B")
                            .contentType("application/json").content(HEADER.replace("QA_ISSUE", "QA_ISSUE_B")))
                    .andExpect(status().isOk()).andExpect(jsonPath("id").value("1"))
                    .andExpect(jsonPath("factoryId").doesNotExist());
            m.perform(get(H + "/1").header("Authorization", "Bearer FULL").header("X-Factory-Id", "A"))
                    .andExpect(status().isOk()).andExpect(jsonPath("issueNo").value("QA_ISSUE"));
            m.perform(get(H + "/1").header("Authorization", "Bearer FULL").header("X-Factory-Id", "B"))
                    .andExpect(status().isOk()).andExpect(jsonPath("issueNo").value("QA_ISSUE_B"));
            m.perform(get(I).header("Authorization", "Bearer FULL").header("X-Factory-Id", "B"))
                    .andExpect(status().isOk()).andExpect(jsonPath("total").value(0));
            m.perform(delete(H).header("Authorization", "Bearer FULL").header("X-Factory-Id", "A")
                            .contentType("application/json").content("[1]"))
                    .andExpect(status().isConflict());
            m.perform(put(I + "/1").header("Authorization", "Bearer FULL").header("X-Factory-Id", "A")
                            .contentType("application/json").content(ITEM.replace("99999999999999.123456", "9.123456")))
                    .andExpect(status().isOk()).andExpect(jsonPath("quantity").value("9.123456"));
            m.perform(delete(I).header("Authorization", "Bearer FULL").header("X-Factory-Id", "A")
                            .contentType("application/json").content("[1,999]"))
                    .andExpect(status().isNotFound());
            assertEquals(2, c.getBean(Fixture.class).business.get("A")
                    .queryForObject("SELECT COUNT(*) FROM mes_material_issue_item", Integer.class));
            m.perform(delete(I).header("Authorization", "Bearer FULL").header("X-Factory-Id", "A")
                            .contentType("application/json").content("[1,2]"))
                    .andExpect(status().isNoContent());
            m.perform(delete(H).header("Authorization", "Bearer FULL").header("X-Factory-Id", "A")
                            .contentType("application/json").content("[1]"))
                    .andExpect(status().isNoContent());
        }
    }

    @Test
    void readOnlyPermissionsAndInvalidReferences() throws Exception {
        try (var c = context()) {
            var m = mvc(c);
            m.perform(get(H).header("Authorization", "Bearer CRUD").header("X-Factory-Id", "A"))
                    .andExpect(status().isOk());
            for (String path : List.of(H, I)) {
                m.perform(post(path).header("Authorization", "Bearer CRUD").header("X-Factory-Id", "A")
                                .contentType("application/json").content(path.equals(H) ? HEADER : ITEM))
                        .andExpect(status().isForbidden());
                m.perform(delete(path).header("Authorization", "Bearer CRUD").header("X-Factory-Id", "A")
                                .contentType("application/json").content("[1]"))
                        .andExpect(status().isForbidden());
            }
            m.perform(post(H).header("Authorization", "Bearer FULL").header("X-Factory-Id", "A")
                            .contentType("application/json")
                            .content(HEADER.replace("\"routingId\":\"1\"", "\"routingId\":\"2\"")))
                    .andExpect(status().isBadRequest());
            m.perform(post(H).header("Authorization", "Bearer FULL").header("X-Factory-Id", "A")
                    .contentType("application/json").content(HEADER)).andExpect(status().isOk());
            for (String invalid : List.of(ITEM.replace("\"materialId\":\"1\"", "\"materialId\":\"2\""),
                    ITEM.replace("\"issueId\":\"1\"", "\"issueId\":\"999\""),
                    ITEM.replace("99999999999999.123456", "-1"), ITEM.replace("99999999999999.123456", "1.1234567"))) {
                m.perform(post(I).header("Authorization", "Bearer FULL").header("X-Factory-Id", "A")
                                .contentType("application/json").content(invalid))
                        .andExpect(status().is4xxClientError());
            }
            m.perform(get(H + "/1").header("Authorization", "Bearer FULL")).andExpect(status().isUnprocessableEntity());
            assertEquals(0, c.getBean(Fixture.class).business.get("A")
                    .queryForObject("SELECT COUNT(*) FROM mes_material_issue_item", Integer.class));
        }
    }
}
