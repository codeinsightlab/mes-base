package dev.mesbase.business.quality.service;

import dev.mesbase.business.quality.dao.DefectStatisticsExtraMapper;
import dev.mesbase.business.quality.dao.ProductionDefectListExtraMapper;
import dev.mesbase.business.production.service.ProductionQuantityService;
import dev.mesbase.business.quality.service.ProductionDefectService;
import dev.mesbase.business.quality.domain.DefectStatistics.Query;
import dev.mesbase.business.quality.domain.QualityTreatmentModels.State;
import dev.mesbase.core.error.FoundationException;
import java.math.BigDecimal;
import java.sql.DriverManager;
import java.sql.Statement;
import java.lang.management.ManagementFactory;
import java.util.UUID;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Signature;
import org.apache.ibatis.session.RowBounds;
import org.apache.ibatis.session.ResultHandler;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

class UnifiedQualityStatisticsTest {
    private final QueryCounter queryCounter = new QueryCounter();

    @Intercepts(@Signature(type = Executor.class, method = "query", args = {
            MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class}))
    public static class QueryCounter implements Interceptor {
        private final AtomicInteger calls = new AtomicInteger();

        void reset() {
            calls.set(0);
        }

        @Override
        public Object intercept(Invocation invocation) throws Throwable {
            calls.incrementAndGet();
            return invocation.proceed();
        }
    }

    private SqlSession database() throws Exception {
        String url = "jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1";
        try (var connection = DriverManager.getConnection(url); var statement = connection.createStatement()) {
            statement.execute("CREATE TABLE mes_production_defect(id BIGINT PRIMARY KEY,declaration_id VARCHAR(36),revision_no INT,execution_code BIGINT,affected_node_id BIGINT,defect_qty DECIMAL(20,6),record_action VARCHAR(16),defect_type_id BIGINT,defect_type_name_snapshot VARCHAR(100),waste_category VARCHAR(20),occurred_at TIMESTAMP)");
            statement.execute("CREATE INDEX defect_revision ON mes_production_defect(declaration_id,revision_no)");
            statement.execute("CREATE TABLE mes_quality_treatment(id VARCHAR(36) PRIMARY KEY,declaration_id VARCHAR(36),declaration_revision INT,execution_code BIGINT,routing_node_id BIGINT,action_type VARCHAR(32),attribution_type VARCHAR(32),related_declaration_id VARCHAR(36),basis VARCHAR(500),returned_qty DECIMAL(20,6),transfer_qty DECIMAL(20,6))");
            statement.execute("CREATE INDEX treatment_declaration ON mes_quality_treatment(declaration_id,declaration_revision)");
            statement.execute("CREATE TABLE routing_node(id BIGINT PRIMARY KEY,operation_id BIGINT)");
            statement.execute("INSERT INTO routing_node VALUES(11,1),(12,2)");
        }
        var config = new Configuration(new Environment("isolated-quality", new JdbcTransactionFactory(),
                new UnpooledDataSource("org.h2.Driver", url, null)));
        config.addInterceptor(queryCounter);
        config.addMapper(ProductionDefectListExtraMapper.class);
        config.addMapper(DefectStatisticsExtraMapper.class);
        return new SqlSessionFactoryBuilder().build(config).openSession();
    }

    private QualityTreatmentService treatments(SqlSession session) {
        return new QualityTreatmentService(null, null, null, null, null, null,
                session.getMapper(ProductionDefectListExtraMapper.class), null);
    }

    private DefectStatisticsService statistics(SqlSession session) {
        return new DefectStatisticsService(session.getMapper(DefectStatisticsExtraMapper.class), treatments(session));
    }

    private Query query(String attribution, int offset, int limit) {
        return new Query(null, null, null, null, attribution, null, null, offset, limit);
    }

    @Test
    void qualityAndProductionFactConsumersDeclareReadOnlyRepeatableReadTransactions() {
        assertRepeatableRead(DefectStatisticsService.class);
        assertRepeatableRead(QualityTreatmentService.class);
        assertRepeatableRead(ProductionDefectService.class);
        assertRepeatableRead(ProductionQuantityService.class);
    }

    private void assertRepeatableRead(Class<?> service) {
        var transaction = service.getAnnotation(Transactional.class);
        assertTrue(transaction != null, service.getSimpleName());
        assertTrue(transaction.readOnly(), service.getSimpleName());
        assertEquals(Isolation.REPEATABLE_READ, transaction.isolation(), service.getSimpleName());
    }

    private void declaration(Statement statement, int id, int type, String action) throws Exception {
        statement.execute("INSERT INTO mes_production_defect VALUES(" + id + ",'d" + id + "',1,1,11,10,'" + action
                + "'," + type + ",'" + (id % 2 == 0 ? "A" : "B") + "','WORKMANSHIP',CURRENT_TIMESTAMP)");
    }

    private void assessment(Statement statement, int id) throws Exception {
        statement.execute("INSERT INTO mes_quality_treatment VALUES('a" + id + "','d" + id
                + "',1,1,11,'ASSESS','INDEPENDENT',NULL,'依据',0,0)");
    }

    @Test
    void groupsCrossSourceAndEventChunksBeforeFilteringPaginationAndTotal() throws Exception {
        try (var session = database(); var statement = session.getConnection().createStatement()) {
            for (int id = 1; id <= 520; id++) {
                declaration(statement, id, 1, "CREATE");
                assessment(statement, id);
            }
            for (int id = 521; id <= 780; id++) declaration(statement, id, 2, "CREATE");
            declaration(statement, 781, 3, "CREATE");
            assessment(statement, 781);
            statement.execute("INSERT INTO mes_quality_treatment VALUES('duplicate','d781',1,1,11,'ASSESS','INDEPENDENT',NULL,'重复',0,0)");
            declaration(statement, 782, 4, "VOID");
            var service = statistics(session);
            var all = service.summarize(query(null, 0, 100));
            // 782 current declarations: four 250-row source reads and four bulk event reads.
            assertEquals(8, queryCounter.calls.get());
            assertEquals(3, all.total());
            assertEquals(3, all.items().size());
            assertEquals(0, new BigDecimal("5200").compareTo(all.items().getFirst().independentQty()));
            assertEquals(2L, all.items().getFirst().typeNameVariants());
            assertEquals("A", all.items().getFirst().typeName());
            assertEquals(0, new BigDecimal("2600").compareTo(all.items().get(1).unverifiedQty()));
            var filtered = service.summarize(query("INDEPENDENT", 1, 1));
            assertEquals(2, filtered.total());
            assertEquals(3L, filtered.items().getFirst().defectTypeId());
            assertNull(filtered.items().getFirst().netDefectQty());
            assertNull(filtered.items().getFirst().independentQty());
            assertEquals(1, filtered.items().getFirst().invalidActions());
            assertEquals(0, service.summarize(query("OVERLAP", 0, 1)).items().getFirst().declaredQty()
                    .compareTo(BigDecimal.TEN));
            assertEquals(1, service.summarize(query("OVERLAP", 0, 1)).total());
            assertEquals(0, service.summarize(query(null, 20, 1)).items().size());
            assertEquals(3, service.summarize(query(null, 20, 1)).total());
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"NULL_ACTION", "LOWER_ACTION", "UNKNOWN_ACTION", "NULL_QTY", "WRONG_EXECUTION", "WRONG_NODE", "OVER_RETURN", "OVER_TRANSFER", "DUPLICATE", "MISSING_OVERLAP", "VOID_OVERLAP", "INVALID_TARGET"})
    void sameInputHasOneLegalityDecisionForDetailStatisticsAndStrictProduction(String anomaly) throws Exception {
        try (var session = database(); var statement = session.getConnection().createStatement()) {
            declaration(statement, 1, 1, "CREATE");
            assessment(statement, 1);
            declaration(statement, 2, 2, "CREATE");
            assessment(statement, 2);
            switch (anomaly) {
                case "NULL_ACTION" ->
                        statement.execute("UPDATE mes_quality_treatment SET action_type=NULL WHERE id='a1'");
                case "LOWER_ACTION" ->
                        statement.execute("UPDATE mes_quality_treatment SET action_type='assess' WHERE id='a1'");
                case "UNKNOWN_ACTION" ->
                        statement.execute("UPDATE mes_quality_treatment SET action_type='REWORK' WHERE id='a1'");
                case "NULL_QTY" ->
                        statement.execute("UPDATE mes_quality_treatment SET returned_qty=NULL WHERE id='a1'");
                case "WRONG_EXECUTION" ->
                        statement.execute("UPDATE mes_quality_treatment SET execution_code=99 WHERE id='a1'");
                case "WRONG_NODE" ->
                        statement.execute("UPDATE mes_quality_treatment SET routing_node_id=99 WHERE id='a1'");
                case "DUPLICATE" ->
                        statement.execute("INSERT INTO mes_quality_treatment SELECT 'dup',declaration_id,declaration_revision,execution_code,routing_node_id,action_type,attribution_type,related_declaration_id,basis,returned_qty,transfer_qty FROM mes_quality_treatment WHERE id='a1'");
                case "OVER_RETURN" ->
                        statement.execute("INSERT INTO mes_quality_treatment VALUES('r','d1',1,1,11,'RETURN_NODE',NULL,NULL,NULL,11,0)");
                case "OVER_TRANSFER" ->
                        statement.execute("INSERT INTO mes_quality_treatment VALUES('t','d1',1,1,11,'TRANSFER_WAREHOUSE',NULL,NULL,NULL,0,11)");
                case "MISSING_OVERLAP" ->
                        statement.execute("UPDATE mes_quality_treatment SET attribution_type='OVERLAP',related_declaration_id='missing' WHERE id='a1'");
                case "VOID_OVERLAP" -> {
                    statement.execute("UPDATE mes_quality_treatment SET attribution_type='OVERLAP',related_declaration_id='d2' WHERE id='a1'");
                    statement.execute("UPDATE mes_production_defect SET record_action='VOID' WHERE id=2");
                }
                case "INVALID_TARGET" -> {
                    statement.execute("UPDATE mes_quality_treatment SET attribution_type='OVERLAP',related_declaration_id='d2' WHERE id='a1'");
                    statement.execute("UPDATE mes_quality_treatment SET routing_node_id=99 WHERE id='a2'");
                }
            }
            var treatments = treatments(session);
            var fact = treatments.currentFacts(null, "d1", null).getFirst();
            assertEquals(State.UNRESOLVED, fact.summary(false).state(), anomaly);
            assertNull(fact.summary(false).netDefectQty(), anomaly);
            assertThrows(FoundationException.class, () -> treatments.nodeTotals("1"), anomaly);
            var row = statistics(session).summarize(query(null, 0, 100)).items().getFirst();
            assertTrue(row.invalidActions() > 0, anomaly);
            assertNull(row.netDefectQty(), anomaly);
            assertNull(row.unverifiedQty(), anomaly);
        }
    }

    @Test
    void overlapTargetOutsideStatisticsFilterIsStillReadAndVoidReleasesOccupancy() throws Exception {
        try (var session = database(); var statement = session.getConnection().createStatement()) {
            declaration(statement, 1, 1, "CREATE");
            assessment(statement, 1);
            declaration(statement, 2, 2, "CREATE");
            assessment(statement, 2);
            statement.execute("UPDATE mes_production_defect SET affected_node_id=12 WHERE id=2");
            statement.execute("UPDATE mes_quality_treatment SET routing_node_id=12,attribution_type='OVERLAP',related_declaration_id='d1' WHERE id='a2'");
            var filtered = statistics(session).summarize(new Query(null, 2L, null, null, "OVERLAP", null, null, 0, 20));
            assertEquals(1, filtered.total());
            assertEquals(0, filtered.items().getFirst().overlapQty().compareTo(BigDecimal.TEN));
            statement.execute("INSERT INTO mes_production_defect VALUES(3,'d1',2,1,11,10,'VOID',1,'A','WORKMANSHIP',CURRENT_TIMESTAMP)");
            session.clearCache();
            var fact = treatments(session).currentFacts(null, "d1", null).getFirst();
            assertEquals(State.VOIDED, fact.summary(false).state());
            assertEquals(0, fact.summary(true).netDefectQty().signum());
            assertEquals(State.UNRESOLVED, treatments(session).currentFacts(null, "d2", null).getFirst().summary(false)
                    .state());
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {1000, 10000})
    void scansRepresentativeStatisticsVolumesWithBoundedQueryCountAndObservableAllocation(int rowCount)
            throws Exception {
        try (var session = database(); var connection = session.getConnection();
             var insert = connection.prepareStatement("INSERT INTO mes_production_defect VALUES(?,?,?,?,?,?,?,?,?,?,CURRENT_TIMESTAMP)")) {
            for (int id = 1; id <= rowCount; id++) {
                insert.setInt(1, id);
                insert.setString(2, "scale-" + id);
                insert.setInt(3, 1);
                insert.setLong(4, 1L);
                insert.setLong(5, 11L);
                insert.setBigDecimal(6, BigDecimal.ONE);
                insert.setString(7, "CREATE");
                insert.setLong(8, id % 100 + 1L);
                insert.setString(9, id / 100 % 2 == 0 ? "A" : "B");
                insert.setString(10, "WORKMANSHIP");
                insert.addBatch();
                if (id % 500 == 0) insert.executeBatch();
            }
            insert.executeBatch();

            if (rowCount == 1000) {
                String sql = "EXPLAIN SELECT d.id,d.declaration_id AS declarationId,d.revision_no AS revision,"
                        + "d.execution_code AS executionCode,d.affected_node_id AS routingNodeId,d.defect_qty AS defectQty,"
                        + "d.record_action AS recordAction,n.operation_id AS operationId,d.defect_type_id AS defectTypeId,"
                        + "d.defect_type_name_snapshot AS typeName,d.waste_category AS wasteCategory "
                        + "FROM mes_production_defect d LEFT JOIN routing_node n ON n.id=d.affected_node_id "
                        + "WHERE NOT EXISTS (SELECT 1 FROM mes_production_defect newer "
                        + "WHERE newer.declaration_id=d.declaration_id AND newer.revision_no>d.revision_no) "
                        + "ORDER BY n.operation_id,d.defect_type_id,d.waste_category,"
                        + "CAST(d.defect_type_name_snapshot AS BINARY(1024)),d.id LIMIT 250 OFFSET 0";
                try (var explain = connection.createStatement(); var plan = explain.executeQuery(sql)) {
                    assertTrue(plan.next());
                    System.out.printf("QUALITY_STATS_H2_EXPLAIN %s%n", plan.getString(1));
                }
            }

            var bean = ManagementFactory.getThreadMXBean();
            long threadId = Thread.currentThread().getId();
            long allocatedBefore = -1L;
            if (bean instanceof com.sun.management.ThreadMXBean threadBean
                    && threadBean.isThreadAllocatedMemorySupported()) {
                if (!threadBean.isThreadAllocatedMemoryEnabled()) threadBean.setThreadAllocatedMemoryEnabled(true);
                allocatedBefore = threadBean.getThreadAllocatedBytes(threadId);
            }
            queryCounter.reset();
            long started = System.nanoTime();
            var result = statistics(session).summarize(query(null, 0, 100));
            long elapsedNanos = System.nanoTime() - started;
            long allocatedBytes = allocatedBefore < 0 ? -1L
                    : ((com.sun.management.ThreadMXBean) bean).getThreadAllocatedBytes(threadId) - allocatedBefore;

            int expectedSourceQueries = rowCount / 250 + 1;
            int expectedEventQueries = rowCount / 250;
            assertEquals(expectedSourceQueries + expectedEventQueries, queryCounter.calls.get());
            assertEquals(100L, result.total());
            assertEquals(100, result.items().size());
            assertEquals(rowCount / 100L, result.items().getFirst().declaredQty().longValueExact());
            assertEquals(2L, result.items().getFirst().typeNameVariants());
            System.out.printf("QUALITY_STATS_H2 rows=%d groups=%d mybatisQueries=%d elapsedMs=%.3f threadAllocatedBytes=%d%n",
                    rowCount, result.total(), queryCounter.calls.get(), elapsedNanos / 1_000_000.0, allocatedBytes);
        }
    }
}
