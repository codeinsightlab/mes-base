package dev.mesbase.business.quality.service;

import dev.mesbase.business.quality.dao.ProductionDefectListExtraMapper;
import dev.mesbase.business.quality.domain.QualityTreatmentModels.State;
import java.sql.DriverManager;
import java.util.List;
import java.util.HashMap;
import org.apache.ibatis.scripting.defaults.DefaultParameterHandler;
import java.util.UUID;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CurrentQualityFactsSqlTest {
    private SqlSession database(int declarations, int transfers) throws Exception {
        String url = "jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1";
        try (var c = DriverManager.getConnection(url); var s = c.createStatement()) {
            s.execute("CREATE TABLE mes_production_defect(id BIGINT PRIMARY KEY,declaration_id VARCHAR(36),revision_no INT,execution_id BIGINT,affected_node_id BIGINT,defect_qty DECIMAL(20,6),record_action VARCHAR(16))");
            s.execute("CREATE UNIQUE INDEX defect_revision ON mes_production_defect(declaration_id,revision_no)");
            s.execute("CREATE TABLE mes_quality_treatment(id VARCHAR(36) PRIMARY KEY,declaration_id VARCHAR(36),declaration_revision INT,execution_id BIGINT,routing_node_id BIGINT,action_type VARCHAR(32),attribution_type VARCHAR(32),related_declaration_id VARCHAR(36),basis VARCHAR(500),returned_qty DECIMAL(20,6),transfer_qty DECIMAL(20,6))");
            s.execute("CREATE INDEX treatment_declaration ON mes_quality_treatment(declaration_id,declaration_revision)");
            for (int i = 0; i < declarations; i++) {
                s.execute("INSERT INTO mes_production_defect VALUES(" + i + ",'d" + i + "',1," + (i % 2 + 1) + ",11," + Math.max(1, transfers) + ",'CREATE')");
                s.execute("INSERT INTO mes_quality_treatment VALUES('a" + i + "','d" + i + "',1," + (i % 2 + 1) + ",11,'ASSESS','INDEPENDENT',NULL,'证据',0,0)");
            }
            for (int i = 0; i < transfers; i++)
                s.execute("INSERT INTO mes_quality_treatment VALUES('t" + i + "','d0',1,1,11,'TRANSFER_WAREHOUSE',NULL,NULL,NULL,0,1)");
        }
        var config = new Configuration(new Environment("isolated-fixture", new JdbcTransactionFactory(), new UnpooledDataSource("org.h2.Driver", url, null)));
        config.addMapper(ProductionDefectListExtraMapper.class);
        return new SqlSessionFactoryBuilder().build(config).openSession();
    }

    @ParameterizedTest
    @ValueSource(ints = {999, 1000, 1001})
    void latestDeclarationsAndBatchAreNotCapped(int count) throws Exception {
        try (var session = database(count, 0)) {
            var mapper = session.getMapper(ProductionDefectListExtraMapper.class);
            var all = mapper.selectCurrentFacts(List.of(1L, 2L), null);
            assertEquals(count, all.size());
            assertTrue(all.stream().allMatch(f -> f.summary(true).state() == State.PENDING));
            assertEquals(all.size(), mapper.selectCurrentFacts(List.of(1L), null)
                    .size() + mapper.selectCurrentFacts(List.of(2L), null).size());
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {4999, 5000, 5001})
    void correspondingEventsAreAggregatedWithoutEntityBudget(int events) throws Exception {
        try (var session = database(1, events - 1)) {
            var fact = session.getMapper(ProductionDefectListExtraMapper.class).selectCurrentFacts(null, "d0")
                    .getFirst();
            assertEquals(0, fact.summary(true).pendingDispositionQty().signum());
            assertEquals(State.TRANSFERRED, fact.summary(true).state());
        }
    }

    @Test
    void oldVersionsVoidAndUnknownFactsHaveDistinctMeaning() throws Exception {
        try (var session = database(3, 0); var s = session.getConnection().createStatement()) {
            s.execute("INSERT INTO mes_production_defect VALUES(10,'d0',2,1,11,2,'REVISE'),(11,'d1',2,2,11,1,'VOID')");
            var mapper = session.getMapper(ProductionDefectListExtraMapper.class);
            var rows = mapper.selectCurrentFacts(List.of(1L, 2L), null);
            assertEquals(2, rows.size());
            assertEquals(State.UNVERIFIED, mapper.selectCurrentFacts(null, "d0").getFirst().summary(true).state());
            s.execute("INSERT INTO mes_quality_treatment VALUES('bad','d0',2,999,11,'UNRECOGNIZED',NULL,NULL,NULL,0,0)");
            session.clearCache();
            assertEquals(State.UNRESOLVED, mapper.selectCurrentFacts(null, "d0").getFirst().summary(false).state());
        }
    }

    @Test
    void thousandsOfValidAssessedRevisionsDoNotBecomeCurrentFactsAndPlanUsesIndexes() throws Exception {
        try (var session = database(1, 0); var statement = session.getConnection().createStatement()) {
            for (int revision = 2; revision <= 5002; revision++) {
                statement.execute("INSERT INTO mes_production_defect VALUES(" + (revision + 10) + ",'d0'," + revision + ",1,11,1,'REVISE')");
                if (revision < 5002)
                    statement.execute("INSERT INTO mes_quality_treatment VALUES('r" + revision + "','d0'," + revision + ",1,11,'ASSESS','INDEPENDENT',NULL,'证据',0,0)");
            }
            var mapper = session.getMapper(ProductionDefectListExtraMapper.class);
            assertEquals(1, mapper.selectCurrentFacts(List.of(1L), null).size());
            assertEquals(State.UNVERIFIED, mapper.selectCurrentFacts(null, "d0").getFirst().summary(true).state());
            var parameters = new HashMap<String, Object>();
            parameters.put("executionIds", null);
            parameters.put("declarationId", "d0");
            var mapped = session.getConfiguration()
                    .getMappedStatement(ProductionDefectListExtraMapper.class.getName() + ".selectCurrentFacts");
            var bound = mapped.getBoundSql(parameters);
            try (var explain = session.getConnection().prepareStatement("EXPLAIN " + bound.getSql())) {
                new DefaultParameterHandler(mapped, parameters, bound).setParameters(explain);
                try (var result = explain.executeQuery()) {
                    assertTrue(result.next());
                    String plan = result.getString(1);
                    assertTrue(plan.contains("DEFECT_REVISION"));
                    assertTrue(plan.contains("TREATMENT_DECLARATION"));
                    System.out.println("B3 isolated H2 EXPLAIN: " + plan);
                }
            }
        }
    }

    @Test
    void duplicateAssessmentsAndWrongIdentityCannotBeHiddenByMaxOrSum() throws Exception {
        try (var session = database(1, 0); var s = session.getConnection().createStatement()) {
            s.execute("INSERT INTO mes_quality_treatment VALUES('dup','d0',1,1,11,'ASSESS','INDEPENDENT',NULL,'证据',0,0)");
            var mapper = session.getMapper(ProductionDefectListExtraMapper.class);
            assertEquals(State.UNRESOLVED, mapper.selectCurrentFacts(null, "d0").getFirst().summary(false).state());
            s.execute("DELETE FROM mes_quality_treatment WHERE id='dup'");
            s.execute("UPDATE mes_quality_treatment SET routing_node_id=999 WHERE id='a0'");
            session.clearCache();
            assertEquals(State.UNRESOLVED, mapper.selectCurrentFacts(null, "d0").getFirst().summary(false).state());
        }
    }

    @Test
    void overlapUsesTheCurrentIndependentDeclarationInTheSameExecution() throws Exception {
        try (var session = database(3, 0); var statement = session.getConnection().createStatement()) {
            statement.execute("UPDATE mes_quality_treatment SET attribution_type='OVERLAP',related_declaration_id='d0' WHERE id='a2'");
            var mapper = session.getMapper(ProductionDefectListExtraMapper.class);
            assertEquals(State.OVERLAP, mapper.selectCurrentFacts(null, "d2").getFirst().summary(true).state());
            statement.execute("UPDATE mes_quality_treatment SET related_declaration_id='d1' WHERE id='a2'");
            session.clearCache();
            assertEquals(State.UNRESOLVED, mapper.selectCurrentFacts(null, "d2").getFirst().summary(false).state());
        }
    }

    @Test
    void enumRecognitionAndAuditOrderingRemainExact() throws Exception {
        try (var session = database(1, 0); var statement = session.getConnection().createStatement()) {
            statement.execute("UPDATE mes_quality_treatment SET action_type='assess' WHERE id='a0'");
            var mapper = session.getMapper(ProductionDefectListExtraMapper.class);
            assertEquals(State.UNRESOLVED, mapper.selectCurrentFacts(null, "d0").getFirst().summary(false).state());
            statement.execute("UPDATE mes_production_defect SET record_action='void' WHERE id=0");
            session.clearCache();
            assertEquals(1, mapper.selectCurrentFacts(List.of(1L), null).size());
            for (String id : List.of("A", "a", "z"))
                statement.execute("INSERT INTO mes_quality_treatment VALUES('" + id + "','d0',1,1,11,'ASSESS','INDEPENDENT',NULL,'证据',0,0)");
            session.clearCache();
            assertEquals(List.of("D:d0:1", "T:A", "T:a", "T:a0", "T:z"), mapper.selectFactIdentities(1L));
        }
    }

}
