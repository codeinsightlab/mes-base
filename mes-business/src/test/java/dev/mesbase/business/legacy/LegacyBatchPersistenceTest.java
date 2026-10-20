package dev.mesbase.business.legacy;

import dev.mesbase.business.legacy.dao.LegacyMigrationMapper;
import dev.mesbase.business.legacy.domain.LegacyHistory.Mapping;
import dev.mesbase.business.production.dao.MesProductionReportMapper;
import dev.mesbase.business.production.dao.MesExecutionPathNodeMapper;
import dev.mesbase.business.production.domain.MesProductionReportEntity;
import dev.mesbase.business.production.domain.MesExecutionPathNodeEntity;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * TEST ONLY: H2 proves bound SQL, key assignment and rollback; live MySQL guards are separate.
 */
class LegacyBatchPersistenceTest {
    private MesProductionReportEntity report(String request) {
        var r = new MesProductionReportEntity();
        r.setExecutionId(1L);
        r.setRoutingNodeId(2L);
        r.setRecordType("REPORT");
        r.setQuantityDelta(new BigDecimal("1.125000"));
        r.setOperatorUserId(7L);
        r.setRecordedBy(100L);
        r.setReportTime(LocalDateTime.of(2025, 1, 2, 3, 4));
        r.setCreateTime(r.getReportTime());
        r.setRequestId(request);
        r.setCommandFingerprint("a".repeat(64));
        return r;
    }

    @Test
    void generatedKeysStayWithTheirReportsAndMappingFailureRollsBackAllNewFacts() {
        var source = new DriverManagerDataSource("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        var jdbc = new JdbcTemplate(source);
        jdbc.execute("CREATE TABLE mes_production_report(id BIGINT AUTO_INCREMENT PRIMARY KEY,execution_id BIGINT,routing_node_id BIGINT,record_type VARCHAR(24),quantity_delta DECIMAL(20,6),reference_report_id BIGINT,operator_user_id BIGINT,recorded_by BIGINT,report_time TIMESTAMP,workstation_id BIGINT,reason VARCHAR(255),create_time TIMESTAMP,request_id VARCHAR(64) UNIQUE,command_fingerprint CHAR(64))");
        jdbc.execute("CREATE TABLE mes_legacy_source_mapping(id CHAR(64) PRIMARY KEY,batch_id VARCHAR(64),parent_id CHAR(64),source_table VARCHAR(64),source_pk VARCHAR(512),source_hash CHAR(64),rule_version VARCHAR(64),entity_kind VARCHAR(32),entity_id BIGINT,source_payload VARCHAR(10000),conversion_payload VARCHAR(10000),created_at TIMESTAMP)");
        jdbc.execute("CREATE TABLE mes_execution_path_node(id BIGINT AUTO_INCREMENT PRIMARY KEY,execution_id BIGINT,routing_node_id BIGINT,sequence_no INT,UNIQUE(execution_id,sequence_no))");
        var config = new Configuration(new Environment("fixture", new JdbcTransactionFactory(), source));
        config.setMapUnderscoreToCamelCase(true);
        config.addMapper(MesProductionReportMapper.class);
        config.addMapper(LegacyMigrationMapper.class);
        config.addMapper(MesExecutionPathNodeMapper.class);
        var factory = new SqlSessionFactoryBuilder().build(config);
        try (var session = factory.openSession(false)) {
            var reports = session.getMapper(MesProductionReportMapper.class);
            var mappings = session.getMapper(LegacyMigrationMapper.class);
            var paths = session.getMapper(MesExecutionPathNodeMapper.class);
            var first = new MesExecutionPathNodeEntity();
            first.setExecutionId(1L);
            first.setRoutingNodeId(2L);
            first.setSequenceNo(1);
            var second = new MesExecutionPathNodeEntity();
            second.setExecutionId(1L);
            second.setRoutingNodeId(3L);
            second.setSequenceNo(2);
            assertEquals(2, paths.insertBatch(List.of(first, second)));
            var a = report("A'中文");
            var b = report("B");
            assertEquals(2, reports.insertBatch(List.of(a, b)));
            assertNotEquals(a.getId(), b.getId());
            assertEquals("A'中文", reports.selectByPrimaryKey(a.getId()).getRequestId());
            assertEquals("B", reports.selectByPrimaryKey(b.getId()).getRequestId());
            var one = new Mapping("1".repeat(64), "B", "p", "pro_reporting_work", "A", "a".repeat(64), "report-driven-local-v2", "REPORT", a.getId(), "{\"name\":\"中文'\"}", "{}", a.getCreateTime());
            var two = new Mapping("2".repeat(64), "B", "p", "pro_reporting_work", "B", "b".repeat(64), "report-driven-local-v2", "REPORT", b.getId(), "{}", "{}", b.getCreateTime());
            assertEquals(2, mappings.insertBatch(List.of(one, two)));
            var identities = mappings.findIdentities(List.of(one.id(), two.id()));
            assertEquals(2, identities.size());
            assertEquals(one.entityId(), identities.stream().filter(v -> v.id().equals(one.id())).findFirst()
                    .orElseThrow().entityId());
            session.commit();
            var pending = new MesExecutionPathNodeEntity();
            pending.setExecutionId(2L);
            pending.setRoutingNodeId(2L);
            pending.setSequenceNo(1);
            paths.insertBatch(List.of(pending));
            var c = report("C");
            reports.insertBatch(List.of(c));
            assertThrows(Exception.class, () -> mappings.insertBatch(List.of(one)));
            session.rollback();
        } catch (Exception e) {
            throw new AssertionError(e);
        }
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM mes_production_report", Integer.class));
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM mes_legacy_source_mapping", Integer.class));
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM mes_execution_path_node", Integer.class));
    }
}
