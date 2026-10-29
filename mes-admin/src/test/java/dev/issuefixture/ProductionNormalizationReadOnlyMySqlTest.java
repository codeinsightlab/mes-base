package dev.issuefixture;

import dev.mesbase.business.issue.dao.MesMaterialIssueInputMapper;

import dev.mesbase.acceptance.AcceptanceBusinessDatabase;
import dev.mesbase.business.directory.domain.DirectoryLabel;
import dev.mesbase.business.directory.service.BusinessDirectoryService;
import dev.mesbase.business.production.dao.MesExecutionPathMapper;
import dev.mesbase.business.production.dao.MesExecutionPathNodeMapper;
import dev.mesbase.business.production.dao.MesProductionExecutionMapper;
import dev.mesbase.business.production.dao.ProductionQuantityMapper;
import dev.mesbase.business.production.dao.RoutingEdgeMapper;
import dev.mesbase.business.production.dao.RoutingNodeMapper;
import dev.mesbase.business.production.dao.WorkorderMapper;
import dev.mesbase.business.production.service.ExecutionPathService;
import dev.mesbase.business.production.service.ProductionQuantityService;
import dev.mesbase.business.quality.dao.MesProductionDefectMapper;
import dev.mesbase.business.quality.dao.MesQualityTreatmentMapper;
import dev.mesbase.business.quality.dao.ProductionDefectListExtraMapper;
import dev.mesbase.business.quality.service.ExecutionQualityReferenceQueryImpl;
import dev.mesbase.business.quality.service.QualityTreatmentService;
import dev.mesbase.core.error.ErrorCode;
import dev.mesbase.core.error.FoundationException;
import dev.mesbase.core.factory.FactoryId;
import dev.mesbase.core.factory.FactoryScope;
import dev.mesbase.infrastructure.persistence.OwnedTransactionManager;
import dev.mesbase.infrastructure.persistence.PersistenceFactories;
import dev.mesbase.platform.execution.FactoryContext;

import java.util.LinkedHashSet;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Real designated MySQL SELECTs through current quantity services; only display labels are mocked.
 */
@EnabledIfEnvironmentVariable(named = "MES_NORMALIZATION_READONLY_ACCEPTANCE", matches = "true")
class ProductionNormalizationReadOnlyMySqlTest {
    @Test
    void convertedExecutionsUseCurrentQuantityAndQualityRules() throws Exception {
        var source = AcceptanceBusinessDatabase.performanceTestSource();
        var jdbc = new JdbcTemplate(source);
        assertEquals("mes_base_dev_perf_test", jdbc.queryForObject("SELECT DATABASE()", String.class));
        assertEquals("03dbc952-7403-11ef-802c-0242ac130002", jdbc.queryForObject("SELECT @@server_uuid", String.class));
        var manager = new OwnedTransactionManager(source);
        var mapperTypes = new LinkedHashSet<>(MaterialIssueHttpTest.MAPPERS);
        var factory = PersistenceFactories.create(source, mapperTypes, PersistenceFactories::pagination, manager::verifyAccess);
        var sql = new SqlSessionTemplate(factory);
        var directory = mock(BusinessDirectoryService.class);
        when(directory.routingLabel(anyLong())).thenAnswer(call -> {
            Long id = call.getArgument(0);
            return new DirectoryLabel(id, "READ_ONLY_LABEL", "数量验收", 1);
        });
        var quality = new QualityTreatmentService(sql.getMapper(MesProductionDefectMapper.class),
                sql.getMapper(MesQualityTreatmentMapper.class), null, null, null, directory,
                sql.getMapper(ProductionDefectListExtraMapper.class), null);
        var qualityQuery = new ExecutionQualityReferenceQueryImpl(sql.getMapper(MesProductionDefectMapper.class), quality);
        var paths = new ExecutionPathService(null, null, sql.getMapper(MesExecutionPathMapper.class),
                sql.getMapper(MesExecutionPathNodeMapper.class), sql.getMapper(MesProductionExecutionMapper.class),
                sql.getMapper(RoutingNodeMapper.class), sql.getMapper(RoutingEdgeMapper.class),
                sql.getMapper(MesMaterialIssueInputMapper.class),
                directory, qualityQuery);
        var quantities = new ProductionQuantityService(sql.getMapper(MesProductionExecutionMapper.class), paths,
                sql.getMapper(MesExecutionPathNodeMapper.class), sql.getMapper(ProductionQuantityMapper.class),
                directory, qualityQuery, sql.getMapper(WorkorderMapper.class));
        var transaction = new TransactionTemplate(manager);
        transaction.setReadOnly(true);
        FactoryContext.bind(new FactoryScope(new FactoryId("TEST_DEV_PERF_20261019")));
        try {
            transaction.executeWithoutResult(status -> {
                assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM mes_production_execution WHERE status NOT IN ('READY','RUNNING','FULLY_RECEIVED','CANCELLED')", Integer.class));
                assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM mes_quality_treatment WHERE action_type NOT IN ('ASSESS','RETURN_NODE','TRANSFER_WAREHOUSE')", Integer.class));
                var ids = new LinkedHashSet<>(jdbc.queryForList("SELECT CAST(source_id AS UNSIGNED) FROM mes_production_conversion_archive WHERE source_table='mes_production_execution' AND JSON_UNQUOTE(JSON_EXTRACT(original_row,'$.status'))='HISTORICAL' ORDER BY source_id", Long.class));
                assertEquals(58, ids.size());
                for (Long id : ids) {
                    var missing = assertThrows(FoundationException.class, () -> quantities.getExecutionQuantities(id));
                    assertEquals(ErrorCode.EXECUTION_NOT_FOUND, missing.code());
                }
                ids.clear();
                ids.addAll(jdbc.queryForList("SELECT DISTINCT e.id FROM mes_production_execution e JOIN mes_material_issue_input f ON f.execution_id=e.id WHERE e.status='RUNNING' AND f.confirmed_at IS NOT NULL AND f.stock_applied=1 ORDER BY e.id LIMIT 20", Long.class));
                for (Long id : ids) {
                    var view = quantities.getExecutionQuantities(id);
                    assertEquals("LOCKED", view.pathStatus(), "execution " + id);
                    assertFalse(view.nodes().isEmpty(), "execution " + id);
                    assertTrue(view.productionInputQty().signum() > 0, "execution " + id);
                    for (var node : view.nodes()) {
                        assertNotNull(node.effectiveOutputQty());
                        assertTrue(node.effectiveOutputQty().signum() >= 0, "execution " + id);
                        assertFalse(node.historicalConflict(), "execution " + id);
                    }
                    String state = jdbc.queryForObject("SELECT status FROM mes_production_execution WHERE id=?", String.class, id);
                    assertEquals("RUNNING".equals(state), view.writable(), "execution " + id);
                }
                System.out.println("Real current-service read-only quantity checks passed for " + ids.size() + " executions");
            });
        } finally {
            FactoryContext.clear();
        }
    }
}
