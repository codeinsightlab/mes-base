package dev.issuefixture;

import dev.mesbase.business.wm.dao.WmWarehouseErpExtraMapper;
import dev.mesbase.framework.sync.SyncCoordinator;
import dev.mesbase.business.production.dao.WorkorderErpExtraMapper;
import dev.mesbase.business.production.service.BusinessSourceRules;
import org.mockito.Mockito;

import static org.mockito.Mockito.mock;

import dev.mesbase.business.quality.service.ExecutionQualityReferenceQueryImpl;
import dev.mesbase.business.quality.service.QualityTreatmentService;
import dev.mesbase.business.quality.dao.MesProductionDefectMapper;

import dev.mesbase.business.production.service.ProductionChainLocks;
import dev.mesbase.business.production.dao.MesProductionReportMapper;
import dev.mesbase.business.production.dao.ProductionQuantityMapper;
import dev.mesbase.business.production.dao.MesExecutionPathMapper;
import dev.mesbase.business.production.dao.MesExecutionPathNodeMapper;

import dev.mesbase.business.issue.service.MaterialIssueErpFacts;
import dev.mesbase.business.issue.dao.MesMaterialIssueErpExtraMapper;

import dev.mesbase.business.integration.warehouse.WarehouseIntegration;
import org.springframework.mock.env.MockEnvironment;

import dev.mesbase.acceptance.AcceptanceBusinessDatabase;
import dev.mesbase.business.basic.dao.MaterialMapper;
import dev.mesbase.business.issue.dao.MesMaterialIssueMapper;
import dev.mesbase.business.issue.dao.MesMaterialIssueItemMapper;
import dev.mesbase.business.issue.domain.MesMaterialIssueCreateCommand;
import dev.mesbase.business.issue.service.MaterialIssueRules;
import dev.mesbase.business.issue.service.impl.MesMaterialIssueServiceImpl;
import dev.mesbase.business.production.dao.MesProductionExecutionMapper;
import dev.mesbase.business.production.dao.MesExecutionAssignmentMapper;
import dev.mesbase.business.production.dao.RoutingMapper;
import dev.mesbase.business.production.dao.RoutingProductMapper;
import dev.mesbase.business.production.dao.WorkorderMapper;
import dev.mesbase.business.basic.dao.UnitMeasureMapper;
import dev.mesbase.business.wm.dao.WmWarehouseMapper;
import dev.mesbase.business.production.domain.MesProductionExecutionCreateCommand;
import dev.mesbase.business.production.domain.MesProductionExecutionExample;
import dev.mesbase.business.production.domain.MesProductionExecutionUpdateCommand;
import dev.mesbase.business.production.service.ProductionExecutionRules;
import dev.mesbase.business.production.service.ProductionIdentityService;
import dev.mesbase.business.production.dao.MesProductionScanIdentityMapper;
import dev.mesbase.business.production.service.impl.MesProductionExecutionServiceImpl;
import dev.mesbase.common.core.domain.entity.SysUser;
import dev.mesbase.common.core.domain.model.LoginUser;
import dev.mesbase.core.error.FoundationException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * LIVE MYSQL: designated SIMPLE_B, generated XML and real rules; one explicit rollback-only transaction.
 */
@EnabledIfEnvironmentVariable(named = "MES_EXECUTION_MYSQL_ACCEPTANCE", matches = "true")
class ProductionExecutionMySqlTest {
    @Test
    void schemaIdentityIssueAndRollback() throws Exception {
        var source = AcceptanceBusinessDatabase.source();
        var config = new Configuration(new Environment("LIVE MYSQL ROLLBACK ONLY", new JdbcTransactionFactory(), source));
        for (var mapper : MaterialIssueHttpTest.MAPPERS) config.addMapper(mapper);
        var sessions = new SqlSessionFactoryBuilder().build(config);
        String tag = "TEST_EXECUTION_" + UUID.randomUUID();
        var user = new SysUser();
        user.setUserId(1L);
        user.setUserName("TEST_ONLY");
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken(new LoginUser(1L, 1L, user, Set.of()), null, List.of()));
        try (var s = sessions.openSession(false)) {
            var connection = s.getConnection();
            assertEquals("mes_base_dev_business_simple_b", connection.getCatalog());
            try (var statement = connection.createStatement()) {
                try (var rows = statement.executeQuery("SELECT COLUMN_COMMENT FROM information_schema.columns WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='mes_production_execution'")) {
                    int count = 0;
                    while (rows.next()) {
                        assertTrue(!rows.getString(1).isBlank());
                        count++;
                    }
                    assertEquals(14, count);
                }
                try (var rows = statement.executeQuery("SELECT COUNT(*) FROM information_schema.statistics WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='mes_production_execution' AND INDEX_NAME<>'PRIMARY' AND NON_UNIQUE=0")) {
                    rows.next();
                    assertEquals(0, rows.getInt(1));
                }
            }
            long workorder, routing;
            try (var q = connection.createStatement(); var rows = q.executeQuery("SELECT w.id,r.id FROM workorder w JOIN material m ON m.id=w.product_id JOIN routing r ON r.id=w.routing_id JOIN routing_product p ON p.routing_id=r.id AND p.material_id=m.id WHERE m.material_type='PRODUCT' AND r.status='PUBLISHED' AND r.published_at IS NOT NULL ORDER BY w.id LIMIT 1")) {
                assertTrue(rows.next(), "Acceptance database needs an existing valid WorkOrder and published routing");
                workorder = rows.getLong(1);
                routing = rows.getLong(2);
            }
            var executions = s.getMapper(MesProductionExecutionMapper.class);
            var issues = s.getMapper(MesMaterialIssueMapper.class);
            var chainLocks = new ProductionChainLocks(s.getMapper(WorkorderMapper.class), executions, issues);
            var rules = new ProductionExecutionRules(executions, s.getMapper(WorkorderMapper.class), s.getMapper(MaterialMapper.class), s.getMapper(RoutingMapper.class), s.getMapper(RoutingProductMapper.class), issues, chainLocks, s.getMapper(MesExecutionPathMapper.class), s.getMapper(MesExecutionPathNodeMapper.class), s.getMapper(MesProductionReportMapper.class), s.getMapper(ProductionQuantityMapper.class), new ExecutionQualityReferenceQueryImpl(s.getMapper(MesProductionDefectMapper.class), mock(QualityTreatmentService.class)), s.getMapper(MesExecutionAssignmentMapper.class), Mockito.mock(BusinessSourceRules.class));
            var service = new MesProductionExecutionServiceImpl(executions, rules);
            var input = new MesProductionExecutionCreateCommand(workorder, routing, new BigDecimal("99999999999999.123456"), "READY", null, null, tag);
            var first = service.insert(input);
            var second = service.insert(input);
            assertNotEquals(first.getId(), second.getId());
            assertNotEquals(first.getExecutionNo(), second.getExecutionNo());
            assertEquals("EXE-" + first.getId(), first.getExecutionNo());
            var updated = service.updateById(first.getId(), new MesProductionExecutionUpdateCommand(workorder, routing, new BigDecimal("1.123456"), "READY", null, null, tag));
            assertEquals(first.getExecutionNo(), updated.getExecutionNo());
            var identity = new ProductionIdentityService(executions, s.getMapper(WorkorderMapper.class), s.getMapper(MaterialMapper.class), s.getMapper(RoutingMapper.class), s.getMapper(MesProductionScanIdentityMapper.class));
            var before = service.selectById(first.getId()).getUpdateTime();
            assertEquals(first.getId(), identity.resolve("EXE:" + first.getId()).executionId());
            assertEquals(before, service.selectById(first.getId()).getUpdateTime());
            var issueRules = new MaterialIssueRules(issues, s.getMapper(MesMaterialIssueItemMapper.class), s.getMapper(MaterialMapper.class), s.getMapper(UnitMeasureMapper.class), s.getMapper(WmWarehouseMapper.class), s.getMapper(WorkorderMapper.class), s.getMapper(RoutingMapper.class), s.getMapper(RoutingProductMapper.class), rules, new MaterialIssueErpFacts(s.getMapper(MesMaterialIssueErpExtraMapper.class)), chainLocks, Mockito.mock(BusinessSourceRules.class));
            var issueService = new MesMaterialIssueServiceImpl(issues, issueRules, new WarehouseIntegration(List.of()));
            var issue = issueService.insert(new MesMaterialIssueCreateCommand(tag, workorder, null, null, null, null, null, null, "RECORDED", tag, first.getId()));
            assertEquals(first.getId(), issue.getExecutionId());
            assertEquals(routing, issue.getRoutingId());
            assertThrows(FoundationException.class, () -> service.deleteByIds(List.of(first.getId())));
            s.rollback();
        } finally {
            SecurityContextHolder.clearContext();
        }
        try (var s = sessions.openSession()) {
            var filter = new MesProductionExecutionExample();
            filter.createCriteria().andRemarkEqualTo(tag);
            assertEquals(0, s.getMapper(MesProductionExecutionMapper.class).countByExample(filter));
        }
    }
}
