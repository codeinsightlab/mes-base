package dev.issuefixture;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;
import tools.jackson.databind.ObjectMapper;

import dev.mesbase.business.basic.domain.MaterialExample;
import dev.mesbase.business.basic.domain.MaterialEntity;
import dev.mesbase.business.production.service.MaterialQuantityRules;
import dev.mesbase.acceptance.FactorySnapshotFixture;
import dev.mesbase.business.basic.dao.MaterialMapper;
import dev.mesbase.business.basic.dao.UnitMeasureMapper;
import dev.mesbase.business.basic.dao.WorkstationMapper;
import dev.mesbase.business.directory.domain.DirectoryLabel;
import dev.mesbase.business.directory.service.BusinessDirectoryService;
import dev.mesbase.business.integration.warehouse.WarehouseIntegration;
import dev.mesbase.business.issue.service.MaterialIssueInputFacts;
import dev.mesbase.business.issue.dao.MesMaterialIssueInputMapper;
import dev.mesbase.business.issue.service.MaterialIssueSubmissionService;
import dev.mesbase.business.issue.service.MaterialIssueVoidService;
import dev.mesbase.business.production.controller.ExecutionActionController;
import dev.mesbase.business.production.controller.MesProductionExecutionController;
import dev.mesbase.business.production.controller.ProductionCompletionController;
import dev.mesbase.business.production.controller.ProductionIdentityController;
import dev.mesbase.business.production.controller.ProductionQuantityErrorAdvice;
import dev.mesbase.business.production.controller.WorkOrderCancellationController;
import dev.mesbase.business.production.controller.WorkOrderController;
import dev.mesbase.business.production.controller.WorkorderMaterialController;
import dev.mesbase.business.production.dao.MesExecutionAssignmentMapper;
import dev.mesbase.business.production.dao.MesExecutionCompletionMapper;
import dev.mesbase.business.production.dao.MesExecutionPathMapper;
import dev.mesbase.business.production.dao.MesExecutionPathNodeMapper;
import dev.mesbase.business.production.dao.MesProductionExecutionMapper;
import dev.mesbase.business.production.dao.MesProductionReceiptMapper;
import dev.mesbase.business.production.dao.MesProductionReportMapper;
import dev.mesbase.business.production.dao.MesProductionScanActivityMapper;
import dev.mesbase.business.production.dao.MesWorkorderCancellationMapper;
import dev.mesbase.business.production.dao.MesWorkorderMaterialMapper;
import dev.mesbase.business.production.dao.ProductionInputExtraMapper;
import dev.mesbase.business.basic.dao.MaterialBomMapper;
import dev.mesbase.business.basic.dao.MaterialBomItemMapper;
import dev.mesbase.business.production.dao.OperationMapper;
import dev.mesbase.business.production.dao.ProductionQuantityMapper;
import dev.mesbase.business.production.dao.ProductionReceiptQueryMapper;
import dev.mesbase.business.production.dao.RoutingEdgeMapper;
import dev.mesbase.business.production.dao.RoutingMapper;
import dev.mesbase.business.production.dao.RoutingNodeMapper;
import dev.mesbase.business.production.dao.RoutingProductMapper;
import dev.mesbase.business.production.dao.WorkOrderExtraMapper;
import dev.mesbase.business.production.dao.WorkorderErpExtraMapper;
import dev.mesbase.business.production.dao.WorkorderMapper;
import dev.mesbase.business.production.domain.OperationExample;
import dev.mesbase.business.production.domain.RoutingExample;
import dev.mesbase.business.production.service.BusinessSourceRules;
import dev.mesbase.business.production.service.ExecutionPathService;
import dev.mesbase.business.production.service.ProductionChainLocks;
import dev.mesbase.business.production.service.ProductionCompletionService;
import dev.mesbase.business.production.service.ProductionExecutionQueryService;
import dev.mesbase.business.production.service.ProductionExecutionRules;
import dev.mesbase.business.production.service.ProductionIdentityService;
import dev.mesbase.business.production.service.ProductionQuantityService;
import dev.mesbase.business.production.service.RoutingReferenceRules;
import dev.mesbase.business.production.service.WorkOrderCancellationService;
import dev.mesbase.business.production.service.WorkOrderCompletionService;
import dev.mesbase.business.production.service.WorkOrderService;
import dev.mesbase.business.production.service.WorkorderErpFacts;
import dev.mesbase.business.production.service.WorkorderMaterialService;
import dev.mesbase.business.production.service.impl.MesProductionExecutionServiceImpl;
import dev.mesbase.business.quality.controller.QualityReleaseController;
import dev.mesbase.business.quality.dao.DefectStatisticsExtraMapper;
import dev.mesbase.business.quality.dao.MesProductionDefectMapper;
import dev.mesbase.business.quality.dao.MesQualityReleaseMapper;
import dev.mesbase.business.quality.dao.MesQualityTreatmentMapper;
import dev.mesbase.business.quality.dao.OperationDefectTypeMapper;
import dev.mesbase.business.quality.dao.ProductionDefectListExtraMapper;
import dev.mesbase.business.quality.service.ExecutionQualityReferenceQueryImpl;
import dev.mesbase.business.quality.service.QualityReleaseService;
import dev.mesbase.business.quality.service.QualityTreatmentService;
import dev.mesbase.business.wm.controller.LocalStockController;
import dev.mesbase.business.wm.controller.StockDocumentController;
import dev.mesbase.business.wm.domain.StockDocumentDirection;
import dev.mesbase.business.wm.domain.StockDocumentType;
import dev.mesbase.business.wm.controller.WarehouseAcceptanceController;
import dev.mesbase.business.wm.controller.WmStockActionController;
import dev.mesbase.business.wm.dao.MesWarehouseAcceptanceMapper;
import dev.mesbase.business.wm.dao.StockDocumentExtraMapper;
import dev.mesbase.business.wm.dao.WmInventoryMapper;
import dev.mesbase.business.wm.dao.WmStockActionMapper;
import dev.mesbase.business.wm.dao.WmStockDocumentLineMapper;
import dev.mesbase.business.wm.dao.WmStockDocumentMapper;
import dev.mesbase.business.wm.dao.WmWarehouseErpExtraMapper;
import dev.mesbase.business.wm.dao.WmWarehouseMapper;
import dev.mesbase.business.wm.service.LocalProductionSupply;
import dev.mesbase.business.wm.service.LocalStockService;
import dev.mesbase.business.wm.service.StockDocumentService;
import dev.mesbase.system.service.AutoCodeNumbering;
import dev.mesbase.business.wm.service.StockReferenceRules;
import dev.mesbase.business.wm.service.WarehouseAcceptanceService;
import dev.mesbase.business.wm.service.impl.WmStockActionServiceImpl;
import dev.mesbase.common.core.domain.entity.SysUser;
import dev.mesbase.common.core.domain.entity.SysDictData;
import dev.mesbase.common.core.domain.model.LoginUser;
import dev.mesbase.common.utils.DictDataLookup;
import dev.mesbase.core.factory.Factory;
import dev.mesbase.core.factory.FactoryId;
import dev.mesbase.core.factory.FactorySelectionService;
import dev.mesbase.core.factory.FactoryStatus;
import dev.mesbase.core.security.AuthorizationService;
import dev.mesbase.framework.security.SystemTokenFilter;
import dev.mesbase.framework.sync.SyncCoordinator;
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
import dev.mesbase.platform.execution.FactoryIntegrationContext;
import dev.mesbase.platform.web.FoundationAccessInterceptor;
import dev.mesbase.platform.web.FoundationErrorAdvice;
import dev.mesbase.platform.web.RequestBoundaryFilter;
import jakarta.servlet.Filter;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
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
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * TEST ONLY: real generated SQL, HTTP authorization and transactions; two separate H2 databases.
 */
class MaterialIssueHttpTest {
    static final Set<Class<?>> MAPPERS = Set.of(MesMaterialIssueInputMapper.class, ProductionInputExtraMapper.class, MaterialBomMapper.class, MaterialBomItemMapper.class, ProductionDefectListExtraMapper.class, StockDocumentExtraMapper.class, OperationDefectTypeMapper.class, DefectStatisticsExtraMapper.class, WmStockDocumentMapper.class, WmStockDocumentLineMapper.class, MesWorkorderCancellationMapper.class, WmWarehouseErpExtraMapper.class, MesQualityReleaseMapper.class, MesWarehouseAcceptanceMapper.class, MesExecutionCompletionMapper.class, MesWorkorderMaterialMapper.class, WmStockActionMapper.class, MesExecutionAssignmentMapper.class, MesProductionScanActivityMapper.class, WmInventoryMapper.class, MesProductionReceiptMapper.class, ProductionReceiptQueryMapper.class, MesProductionDefectMapper.class, MesQualityTreatmentMapper.class, WorkstationMapper.class, MesProductionReportMapper.class, ProductionQuantityMapper.class, MesExecutionPathMapper.class, MesExecutionPathNodeMapper.class, WorkorderErpExtraMapper.class, WorkOrderExtraMapper.class, RoutingNodeMapper.class, RoutingEdgeMapper.class, OperationMapper.class, MesProductionExecutionMapper.class, MaterialMapper.class, UnitMeasureMapper.class, WmWarehouseMapper.class, RoutingMapper.class, RoutingProductMapper.class, WorkorderMapper.class);

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
                for (String name : List.of("V2__simple_business_capabilities.sql", "V3__operation_routing.sql", "V4__routing_operation_instruction_snapshot.sql", "V9__routing_graph.sql", "V8__warehouse_inventory.sql", "V11__material_issue.sql", "V12__material_issue_quantity_precision.sql", "V13__workorder_batch_route.sql", "V16__production_execution.sql", "V18__material_issue_void.sql", "V19__local_void_external_effect.sql", "V20__material_issue_erp_extra.sql", "V21__workorder_external_extra.sql", "V22__unverified_legacy_erp_provider.sql", "V23__production_core_v1.sql", "V24__operation_user_execution_assignment.sql", "V25__production_report_ledger.sql", "V26__production_defect.sql", "V27__production_report_request.sql", "V28__production_receipt.sql", "V29__single_material_production_input.sql", "V32__quality_treatment_facts.sql", "V33__admin_report_command_fingerprint.sql", "V34__local_stock_action.sql", "V35__local_production_closure.sql", "V36__business_source_isolation.sql", "V37__workorder_cancellation.sql", "V38__local_stock_documents.sql", "V39__operation_defect_types.sql", "V44__unified_inbound_documents.sql", "V47__quality_disposition_destinations.sql", "V48_1__quality_schema_cleanup.sql", "V50__quality_declaration_single_node.sql", "V63__remove_routing_product_material_snapshots.sql")) {
                    String directory = name.startsWith("V63__") ? "db/history/business-v63/" : "db/business/";
                    String ddl = Files.readString(Path.of("mes-admin/src/main/resources/" + directory + name))
                            .replace("\r\n", "\n")
                            .replace(") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT=", ") COMMENT ")
                            .replaceAll("(?m)^\\s*factory_id [^\\n]+\\n", "")
                            .replace("factory_id,", "")
                            .replace("factory_id, ", "")
                            .replace(" CHARACTER SET utf8mb4 COLLATE utf8mb4_bin", "");
                    if (name.equals("V13__workorder_batch_route.sql"))
                        ddl = ddl.substring(0, ddl.indexOf("CREATE TABLE production_batch"));
                    if (name.equals("V18__material_issue_void.sql"))
                        ddl = ddl.replace(",\n ADD COLUMN", ";\nALTER TABLE mes_material_issue ADD COLUMN");
                    if (name.equals("V20__material_issue_erp_extra.sql"))
                        ddl = ddl.replace(",\n DROP COLUMN", ";\nALTER TABLE mes_material_issue DROP COLUMN");
                    if (name.equals("V21__workorder_external_extra.sql"))
                        ddl = ddl.replace(", DROP COLUMN", "; ALTER TABLE workorder DROP COLUMN");
                    if (name.equals("V23__production_core_v1.sql"))
                        ddl = ddl.replace(",\n MODIFY COLUMN", ";\nALTER TABLE mes_material_issue ALTER COLUMN");
                    if (name.equals("V25__production_report_ledger.sql")) ddl = ddl.split("-- 服务和数据库")[0];
                    if (name.equals("V28__production_receipt.sql") || (name.equals("V34__local_stock_action.sql") || name.equals("V35__local_production_closure.sql")) || name.equals("V26__production_defect.sql") || name.equals("V32__quality_treatment_facts.sql"))
                        ddl = ddl.split("CREATE TRIGGER")[0].replace(" COLLATE utf8mb4_bin", "")
                                .replace(" CHARACTER SET ascii COLLATE ascii_bin", "");
                    if (name.equals("V33__admin_report_command_fingerprint.sql"))
                        ddl = ddl.replace(" CHARACTER SET ascii COLLATE ascii_bin", "");
                    if (name.equals("V27__production_report_request.sql"))
                        ddl = ddl.replace(" CHARACTER SET ascii COLLATE ascii_bin", "")
                                .replace(",\n ADD UNIQUE KEY uk_report_request_id(request_id)", ";\nALTER TABLE mes_production_report ADD CONSTRAINT uk_report_request_id UNIQUE(request_id)");
                    if (name.equals("V29__single_material_production_input.sql")) {
                        ddl = ddl.substring(ddl.indexOf("CREATE TABLE mes_material_issue_input"))
                                .split("-- 旧库存")[0].replace(" CHARACTER SET ascii COLLATE ascii_bin", "");
                        jdbc.execute("ALTER TABLE wm_inventory ADD batch_no VARCHAR(128)");
                        jdbc.execute("ALTER TABLE wm_inventory ADD bin_code VARCHAR(64)");
                        // Match the real V29 precision; four-decimal fixture balances hide cumulative rounding errors.
                        jdbc.execute("ALTER TABLE wm_inventory ALTER COLUMN quantity SET DATA TYPE DECIMAL(20,6)");
                        jdbc.execute("ALTER TABLE wm_inventory ALTER COLUMN available_quantity SET DATA TYPE DECIMAL(20,6)");
                        jdbc.execute("ALTER TABLE wm_inventory DROP CONSTRAINT uk_inventory_factory_material_warehouse");
                    }
                    if (name.equals("V36__business_source_isolation.sql"))
                        ddl = ddl.split("DELIMITER")[0].replace(",\n ADD COLUMN", ";\nALTER TABLE workorder_erp_extra ADD COLUMN");
                    if (name.equals("V38__local_stock_documents.sql"))
                        ddl = ddl.replace(",\n ADD COLUMN", ";\nALTER TABLE wm_stock_action ADD COLUMN");
                    if (name.equals("V44__unified_inbound_documents.sql"))
                        ddl = ddl.replace(",\n ADD KEY idx_stock_document_execution(", ";\nCREATE INDEX idx_stock_document_execution ON wm_stock_document(");
                    if (name.equals("V47__quality_disposition_destinations.sql")) {
                        ddl = ddl.replace(",\n ADD COLUMN", ";\nALTER TABLE mes_quality_treatment ADD COLUMN")
                                .replace(",\n ADD KEY idx_quality_treatment_stock_document(stock_document_id)", ";\nCREATE INDEX idx_quality_treatment_stock_document ON mes_quality_treatment(stock_document_id)")
                                .replace(",\n ADD KEY idx_report_quality_event(source_quality_event_id,id)", ";\nCREATE INDEX idx_report_quality_event ON mes_production_report(source_quality_event_id,id)");
                    }
                    if (name.equals("V48_1__quality_schema_cleanup.sql"))
                        ddl = ddl.replaceAll("(?m)^ALTER TABLE mes_quality_treatment DROP INDEX[^;]+;", "DROP INDEX idx_quality_treatment_parent;")
                                .replaceAll("(?s)ALTER TABLE mes_quality_treatment\\s+MODIFY COLUMN.*", "");
                    if (name.equals("V37__workorder_cancellation.sql")) ddl = ddl.split("CREATE TRIGGER")[0];
                    for (String statement : ddl.split(";")) if (!statement.isBlank()) jdbc.execute(statement);
                }
                // Final internal schema: only unified document identities remain.
                jdbc.execute("DROP TABLE mes_material_issue_item");
                jdbc.execute("DROP TABLE mes_material_issue");
                jdbc.execute("ALTER TABLE mes_material_issue_input ALTER COLUMN issue_id RENAME TO document_id");
                jdbc.execute("ALTER TABLE mes_material_issue_input ADD business_occurred_at TIMESTAMP");
                jdbc.execute("DROP TABLE mes_material_issue_submission");
                jdbc.execute("ALTER TABLE wm_stock_document ADD direction VARCHAR(8) DEFAULT 'OUT'");
                jdbc.execute("ALTER TABLE wm_stock_document ADD create_request_id VARCHAR(36)");
                jdbc.execute("ALTER TABLE wm_stock_document ADD paired_document_id BIGINT");
                jdbc.execute("ALTER TABLE wm_stock_document ADD CONSTRAINT uk_stock_document_create_request UNIQUE(create_request_id)");
                jdbc.update("UPDATE wm_stock_document SET direction='IN' WHERE document_type IN ('PURCHASE_RECEIPT','PRODUCTION_RECEIPT','OTHER_RECEIPT','DEFECT_RECEIPT','SCRAP_RECEIPT')");
                jdbc.execute("DROP TABLE mes_material_issue_erp_extra");
                try (var connection = source.getConnection()) {
                    ScriptUtils.executeSqlScript(connection, new FileSystemResource("mes-admin/src/main/resources/db/business/V48__warehouse_type.sql"));
                }
                try (var connection = source.getConnection()) {
                    ScriptUtils.executeSqlScript(connection, new FileSystemResource("mes-admin/src/main/resources/db/business/V55__material_bom.sql"));
                }
                jdbc.execute("ALTER TABLE material_bom ADD is_default TINYINT NOT NULL DEFAULT 0");
                for (String column : List.of("bom_id BIGINT", "bom_base_qty DECIMAL(20,6)", "bom_component_qty DECIMAL(20,6)", "output_unit_id BIGINT", "frozen_at TIMESTAMP(6)"))
                    jdbc.execute("ALTER TABLE mes_workorder_material ADD " + column);
                jdbc.execute("ALTER TABLE wm_stock_document ADD source_request_json TEXT");
                jdbc.execute("ALTER TABLE wm_stock_document ADD CONSTRAINT uk_stock_document_request_identity UNIQUE(post_request_id)");
                // TEST ONLY: these fresh fixture objects are explicitly local; no production migration default.
                jdbc.execute("ALTER TABLE workorder ALTER COLUMN source SET DEFAULT 'LOCAL'");
                jdbc.execute("ALTER TABLE wm_warehouse ALTER COLUMN source SET DEFAULT 'LOCAL'");
                // TEST ONLY H2 equivalent of V60: retired release snapshot is absent on new completion facts.
                jdbc.execute("ALTER TABLE mes_execution_completion ALTER COLUMN released_qty DROP NOT NULL");
                jdbc.execute("ALTER TABLE mes_production_execution DROP COLUMN execution_no");
                jdbc.execute("ALTER TABLE mes_production_execution ADD batch_no VARCHAR(128)");
                String scanDdl = Files.readString(Path.of("mes-admin/src/main/resources/db/business/V52__execution_batch_and_scan_activity.sql"));
                scanDdl = scanDdl.substring(scanDdl.indexOf("CREATE TABLE mes_production_scan_activity")).split(";")[0]
                        .replace(") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT=", ") COMMENT ");
                jdbc.execute(scanDdl);
                // TEST ONLY H2 equivalent of V64; rules are persisted per business datasource.
                jdbc.execute("ALTER TABLE unit_measure ADD must_be_whole_number TINYINT DEFAULT 0 NOT NULL");
                jdbc.execute("ALTER TABLE unit_measure ADD quantity_precision TINYINT DEFAULT 6 NOT NULL");
                jdbc.update("INSERT INTO material(id,code,name,unit_id,status) VALUES(1,'M1','物料一',1,'ENABLED')");
                jdbc.update("INSERT INTO workorder(id,code,product_id,planned_qty,routing_id,status,revision,create_time,update_time) VALUES(1,'WO1',1,1000,1,'DRAFT',0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");
                jdbc.update("INSERT INTO unit_measure(id,code,name,status) VALUES(1,'KG','千克','ENABLED')");
                jdbc.update("INSERT INTO wm_warehouse(id,code,name,status) VALUES(1,'W1','验收仓库','ENABLED')");
                jdbc.update("INSERT INTO routing(id,code,name,version,status,revision,published_at) VALUES(1,'R1','已发布版本',1,'PUBLISHED',0,CURRENT_TIMESTAMP),(2,'R1','草稿版本',2,'DRAFT',0,NULL)");
                jdbc.update("INSERT INTO routing_product(routing_id,material_id) VALUES(1,1)");
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
    @Import({StockDocumentOwnedTransactionsHttpTest.GuardedCenter.class, MaterialQuantityRules.class, WorkOrderCompletionService.class, StockDocumentService.class, StockDocumentController.class, WorkOrderCancellationService.class, WorkOrderCancellationController.class, BusinessSourceRules.class, StockReferenceRules.class, ProductionCompletionService.class, ProductionCompletionController.class, WarehouseAcceptanceService.class, WarehouseAcceptanceController.class, QualityReleaseService.class, QualityReleaseController.class, WorkorderMaterialService.class, WorkorderMaterialController.class, LocalStockController.class, WmStockActionController.class, WmStockActionServiceImpl.class, LocalStockService.class, LocalProductionSupply.class, QualityTreatmentService.class, MaterialIssueInputFacts.class, ProductionQuantityService.class, ProductionQuantityErrorAdvice.class, ExecutionQualityReferenceQueryImpl.class, ProductionExecutionQueryService.class, ProductionChainLocks.class, ExecutionPathService.class, ExecutionActionController.class, MaterialIssueSubmissionService.class, WorkorderErpFacts.class, WarehouseIntegration.class, MaterialIssueVoidService.class, ProductionExecutionRules.class, ProductionIdentityService.class, MesProductionExecutionServiceImpl.class, MesProductionExecutionController.class, ProductionIdentityController.class, WorkOrderController.class, WorkOrderService.class, RoutingReferenceRules.class, FoundationErrorAdvice.class})
    static class Web implements WebMvcConfigurer {
        @org.springframework.beans.factory.annotation.Autowired
        private AuthorizationService authorization;

        @Bean
        FactorySnapshotFixture factorySnapshots() {
            return new FactorySnapshotFixture();
        }

        @Bean
        BusinessDirectoryService directoryLabels(RoutingMapper routes, OperationMapper operations,
                                                 MaterialMapper materials) {
            var directory = mock(BusinessDirectoryService.class);
            when(directory.materialEntities(any())).thenAnswer(call -> {
                Collection<Long> ids = call.getArgument(0);
                var result = new LinkedHashMap<Long, MaterialEntity>();
                if (!ids.isEmpty()) {
                    var filter = new MaterialExample();
                    filter.createCriteria().andIdIn(List.copyOf(ids));
                    for (var row : materials.selectByExample(filter)) result.put(row.getId(), row);
                }
                return result;
            });
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
                if (row == null) return null;
                return new DirectoryLabel(row.getId(), row.getCode(), row.getName(), row.getVersion());
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
        SyncCoordinator syncCoordinator() {
            return mock(SyncCoordinator.class);
        }

        @Bean
        AutoCodeNumbering autoCodeNumbering() {
            var numbering = mock(AutoCodeNumbering.class);
            var generated = new AtomicInteger();
            when(numbering.getDocNo(any(), any())).thenAnswer(call -> "TEST_WO_" + generated.incrementAndGet());
            when(numbering.getBusinessDocNo(any(), any())).thenAnswer(call -> "TEST_DOC_" + generated.incrementAndGet());
            return numbering;
        }

        @Bean
        DictDataLookup documentTypeDictionaries() {
            return dictionary -> {
                StockDocumentDirection direction = "stock_document_in_type".equals(dictionary)
                        ? StockDocumentDirection.IN : StockDocumentDirection.OUT;
                return Arrays.stream(StockDocumentType.values())
                        .filter(type -> type.getDirection() == direction)
                        .map(type -> {
                            var row = new SysDictData();
                            row.setDictValue(type.getValue());
                            row.setDictLabel(type.getDescription());
                            row.setStatus("0");
                            row.setDictSort(10L);
                            return row;
                        }).toList();
            };
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
        MesProductionScanActivityMapper scanMapper(SqlSessionFactory f) {
            return new SqlSessionTemplate(f).getMapper(MesProductionScanActivityMapper.class);
        }

        @Bean
        MesProductionReceiptMapper receiptMapper(SqlSessionFactory f) {
            return new SqlSessionTemplate(f).getMapper(MesProductionReceiptMapper.class);
        }

        @Bean
        MaterialBomMapper materialBomMapper(SqlSessionFactory f) {
            return new SqlSessionTemplate(f).getMapper(MaterialBomMapper.class);
        }

        @Bean
        MaterialBomItemMapper materialBomItemMapper(SqlSessionFactory f) {
            return new SqlSessionTemplate(f).getMapper(MaterialBomItemMapper.class);
        }

        @Bean
        MesMaterialIssueInputMapper materialIssueInputMapper(SqlSessionFactory f) {
            return new SqlSessionTemplate(f).getMapper(MesMaterialIssueInputMapper.class);
        }

        @Bean
        ProductionInputExtraMapper productionInputExtraMapper(SqlSessionFactory f) {
            return new SqlSessionTemplate(f).getMapper(ProductionInputExtraMapper.class);
        }

        @Bean
        MesWorkorderMaterialMapper mesWorkorderMaterialMapper(SqlSessionFactory f) {
            return new SqlSessionTemplate(f).getMapper(MesWorkorderMaterialMapper.class);
        }

        @Bean
        OperationDefectTypeMapper operationDefectTypeMapper(SqlSessionFactory f) {
            return new SqlSessionTemplate(f).getMapper(OperationDefectTypeMapper.class);
        }

        @Bean
        ProductionDefectListExtraMapper productionDefectListExtraMapper(SqlSessionFactory f) {
            return new SqlSessionTemplate(f).getMapper(ProductionDefectListExtraMapper.class);
        }

        @Bean
        DefectStatisticsExtraMapper defectStatisticsExtraMapper(SqlSessionFactory f) {
            return new SqlSessionTemplate(f).getMapper(DefectStatisticsExtraMapper.class);
        }

        @Bean
        AuthorizationService qualityAuthorization() {
            return (actor, scope, permission) -> "full".equals(actor.id())
                    || "revise".equals(actor.id()) && permission.code().equals("business:quality:defect:revise")
                    || "query".equals(actor.id()) && permission.code().equals("workorder:query")
                    || "create".equals(actor.id()) && permission.code().equals("business:quality:defect:create")
                    || "issue_query".equals(actor.id()) && permission.code().equals("business:mes:material:issue:query")
                    || "issue_item".equals(actor.id()) && permission.code()
                    .equals("business:mes:material:issue:item:list")
                    || "crud".equals(actor.id()) && (permission.code().endsWith(":list")
                    || permission.code().endsWith(":query") || permission.code().equals("business:quality:defect:read")
                    || permission.code().equals("business:quality:defect:create"));
        }

        @Bean
        MesQualityReleaseMapper mesQualityReleaseMapper(SqlSessionFactory f) {
            return new SqlSessionTemplate(f).getMapper(MesQualityReleaseMapper.class);
        }

        @Bean
        StockDocumentExtraMapper stockDocumentExtraMapper(SqlSessionFactory f) {
            return new SqlSessionTemplate(f).getMapper(StockDocumentExtraMapper.class);
        }

        @Bean
        MesWarehouseAcceptanceMapper mesWarehouseAcceptanceMapper(SqlSessionFactory f) {
            return new SqlSessionTemplate(f).getMapper(MesWarehouseAcceptanceMapper.class);
        }

        @Bean
        MesWorkorderCancellationMapper mesWorkorderCancellationMapper(SqlSessionFactory f) {
            return new SqlSessionTemplate(f).getMapper(MesWorkorderCancellationMapper.class);
        }

        @Bean
        MesExecutionCompletionMapper mesExecutionCompletionMapper(SqlSessionFactory f) {
            return new SqlSessionTemplate(f).getMapper(MesExecutionCompletionMapper.class);
        }

        @Bean
        WmStockDocumentMapper stockDocumentMapper(SqlSessionFactory f) {
            return new SqlSessionTemplate(f).getMapper(WmStockDocumentMapper.class);
        }

        @Bean
        WmStockDocumentLineMapper stockDocumentLineMapper(SqlSessionFactory f) {
            return new SqlSessionTemplate(f).getMapper(WmStockDocumentLineMapper.class);
        }

        @Bean
        WmStockActionMapper stockActionMapper(SqlSessionFactory f) {
            return new SqlSessionTemplate(f).getMapper(WmStockActionMapper.class);
        }

        @Bean
        WmInventoryMapper inventoryMapper(SqlSessionFactory f) {
            return new SqlSessionTemplate(f).getMapper(WmInventoryMapper.class);
        }


        @Bean
        WorkorderErpExtraMapper workorderErpMapper(SqlSessionFactory f) {
            return new SqlSessionTemplate(f).getMapper(WorkorderErpExtraMapper.class);
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
        WmWarehouseErpExtraMapper warehouseExternal(SqlSessionFactory f) {
            return new SqlSessionTemplate(f).getMapper(WmWarehouseErpExtraMapper.class);
        }

        @Bean
        WmWarehouseMapper warehouses(SqlSessionFactory f) {
            return new SqlSessionTemplate(f).getMapper(WmWarehouseMapper.class);
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
                user.setUserName(value.endsWith("NONE") ? "none" : value.endsWith("CRUD") ? "crud" : value.endsWith("CREATE") ? "create" : value.endsWith("REVISE") ? "revise" : value.endsWith("ISSUE_QUERY") ? "issue_query" : value.endsWith("ISSUE_ITEM") ? "issue_item" : value.endsWith("QUERY") ? "query" : "full");
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
                        authorization));
            } catch (Exception failure) {
                throw new IllegalStateException(failure);
            }
        }
    }

    /**
     * Explicit synthetic external provenance; never a production migration or proof of real ERP sync.
     */
    static void externalFixture(AnnotationConfigWebApplicationContext c) {
        String provider = FactorySnapshotFixture.current(c, "A").erpType();
        var j = c.getBean(Fixture.class).business.get("A");
        j.update("UPDATE workorder SET source='EXTERNAL' WHERE id=1");
        j.update("UPDATE workorder_erp_extra SET external_id='TEST-ERP-ORDER',provider=?,binding_key='TEST_A',configuration_revision=1 WHERE id=1", provider);
        j.update("UPDATE wm_warehouse SET source='EXTERNAL' WHERE id=1");
        j.update("INSERT INTO wm_warehouse_erp_extra VALUES(1,'TEST-ERP-WAREHOUSE',?,'TEST_A',1)", provider);
    }

    static AnnotationConfigWebApplicationContext context() {
        var c = new AnnotationConfigWebApplicationContext();
        c.setServletContext(new MockServletContext());
        c.getEnvironment().getPropertySources()
                .addFirst(new MapPropertySource("TEST ONLY", Map.of("mes.business.enabled", "true")));
        c.register(Web.class);
        c.refresh();
        return c;
    }

    static MockMvc mvc(AnnotationConfigWebApplicationContext c) {
        return MockMvcBuilders.webAppContextSetup(c)
                .addFilters(new RequestBoundaryFilter(), (Filter) (req, res, chain) -> {
                    String factory = ((HttpServletRequest) req).getHeader("X-Factory-Id");
                    if (factory == null) {
                        chain.doFilter(req, res);
                        return;
                    }
                    var snapshot = FactorySnapshotFixture.current(c, factory);
                    final IOException[] failure = new IOException[1];
                    final ServletException[] servlet = new ServletException[1];
                    FactoryIntegrationContext.run(snapshot, () -> {
                        try {
                            chain.doFilter(req, res);
                        } catch (IOException e) {
                            failure[0] = e;
                        } catch (ServletException e) {
                            servlet[0] = e;
                        }
                    });
                    if (failure[0] != null) throw failure[0];
                    if (servlet[0] != null) throw servlet[0];
                }, c.getBean(FilterChainProxy.class)).build();
    }

}
