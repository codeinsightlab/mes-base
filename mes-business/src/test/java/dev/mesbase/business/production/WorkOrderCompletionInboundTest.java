package dev.mesbase.business.production;

import com.github.pagehelper.PageHelper;
import dev.mesbase.business.production.dao.MesProductionExecutionMapper;
import dev.mesbase.business.production.dao.WorkOrderExtraMapper;
import dev.mesbase.business.production.dao.WorkorderMapper;
import dev.mesbase.business.production.domain.MesProductionExecutionEntity;
import dev.mesbase.business.production.domain.MesProductionExecutionExample;
import dev.mesbase.business.production.domain.ProductionReportModels.ExecutionQuantity;
import dev.mesbase.business.production.domain.ProductionReportModels.NodeQuantity;
import dev.mesbase.business.production.domain.WorkOrderModels.Allocation;
import dev.mesbase.business.production.domain.WorkOrderModels.CompletionState;
import dev.mesbase.business.production.domain.WorkorderEntity;
import dev.mesbase.business.production.service.BusinessSourceRules;
import dev.mesbase.business.production.service.ProductionQuantityService;
import dev.mesbase.business.production.service.WorkOrderCompletionService;
import dev.mesbase.business.quality.dao.ProductionDefectListExtraMapper;
import dev.mesbase.business.quality.service.QualityTreatmentService;
import dev.mesbase.business.wm.dao.StockDocumentExtraMapper;
import dev.mesbase.business.wm.dao.WmStockDocumentMapper;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Signature;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.LocalCacheScope;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Actual closure loop and Mapper SQL on isolated H2; unrelated domain reads are fixed mocks.
 */
class WorkOrderCompletionInboundTest {
    private static final int BENCHMARK_EXECUTIONS = 1001;

    @Test
    @EnabledIfSystemProperty(named = "r2.benchmark", matches = "true")
    void compareThreeRoundsOnTheSameFixture() throws Exception {
        try (var fixture = new Fixture(BENCHMARK_EXECUTIONS)) {
            String fingerprint = fixture.fingerprint();
            assertNull(fixture.service.blockedReason(1L)); // One identical untimed warm-up in each version.
            for (int round = 1; round <= 3; round++) {
                fixture.counter.calls.clear();
                long started = System.nanoTime();
                assertNull(fixture.service.blockedReason(1L));
                long elapsed = System.nanoTime() - started;
                assertEquals(fingerprint, fixture.fingerprint());
                System.out.printf("R2_BENCH label=%s round=%d executions=%d receipts=%d elapsedMs=%.3f proofQueries=%d dataSha256=%s%n",
                        System.getProperty("r2.benchmark.label", "unspecified"), round, BENCHMARK_EXECUTIONS,
                        BENCHMARK_EXECUTIONS * 2, elapsed / 1_000_000.0, fixture.counter.total(), fingerprint);
            }
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 99, 100, 101})
    void eachNeededPageLoadsOnlyOneBatchAndTheFinalExecutionIsIncluded(int count) throws Exception {
        try (var fixture = new Fixture(count)) {
            String result = fixture.service.blockedReason(1L);
            if (count == 0) assertEquals("工单没有正式有效生产执行", result);
            else {
                assertNull(result);
                verify(fixture.quality).receiptBlockReason("R2-E-" + count);
            }
            assertEquals((count + 99) / 100, fixture.counter.total());
            assertTrue(fixture.counter.calls.keySet().stream().allMatch("productionInboundSummaries"::equals));
        }
    }

    @Test
    void anExecutionStatusFailureDoesNotLoadReceiptProofs() throws Exception {
        try (var fixture = new Fixture(101)) {
            fixture.rows.getFirst().setStatus("READY");
            assertEquals("工单仍有未完全入库的执行", fixture.service.blockedReason(1L));
            assertEquals(0, fixture.counter.total());
            verify(fixture.quantities, never()).getExecutionQuantities(anyString());
        }
    }

    @Test
    void firstQuantityFailureDoesNotLoadReceiptProofs() throws Exception {
        try (var fixture = new Fixture(101)) {
            fixture.projections.put("R2-E-1", missingInput("R2-E-1"));
            assertEquals("缺少正式投入或仍有未决投产意图", fixture.service.blockedReason(1L));
            assertEquals(0, fixture.counter.total());
            verify(fixture.quality, never()).receiptBlockReason(anyString());
        }
    }

    @Test
    void anUnlockedPathStillBlocksBeforeLoadingReceiptProofs() throws Exception {
        try (var fixture = new Fixture(1)) {
            fixture.rows.getFirst().setRoutingId(11L);
            assertEquals("执行实际路径尚未锁定", fixture.service.blockedReason(1L));
            assertEquals(0, fixture.counter.total());
            verify(fixture.quality, never()).receiptBlockReason(anyString());
        }
    }

    @Test
    void nextPageQuantityFailureDoesNotLoadTheNextPageBatch() throws Exception {
        try (var fixture = new Fixture(101)) {
            fixture.projections.put("R2-E-101", missingInput("R2-E-101"));
            assertEquals("缺少正式投入或仍有未决投产意图", fixture.service.blockedReason(1L));
            assertEquals(1, fixture.counter.total());
        }
    }

    @Test
    void aLaterInvalidProofDoesNotPreemptTheEarlierQualityBlocker() throws Exception {
        try (var fixture = new Fixture(2)) {
            fixture.execute("UPDATE wm_stock_action SET request_hash='wrong' WHERE id='action-3'");
            when(fixture.quality.receiptBlockReason("R2-E-1")).thenReturn("质量待核实");
            assertEquals("质量待核实", fixture.service.blockedReason(1L));
            assertEquals(1, fixture.counter.total());
            verify(fixture.quantities, never()).getExecutionQuantities("R2-E-2");
        }
    }

    @Test
    void quantityValidationStillPrecedesAnAlreadyPrefetchedInvalidProof() throws Exception {
        try (var fixture = new Fixture(2)) {
            fixture.execute("UPDATE wm_stock_action SET request_hash='wrong' WHERE id='action-3'");
            fixture.projections.put("R2-E-2", missingInput("R2-E-2"));
            assertEquals("缺少正式投入或仍有未决投产意图", fixture.service.blockedReason(1L));
            assertEquals(1, fixture.counter.total());
            verify(fixture.quality, never()).receiptBlockReason("R2-E-2");
        }
    }

    @Test
    void validQuantityDoesNotHideAnAdditionalInvalidLine() throws Exception {
        try (var fixture = new Fixture(1)) {
            fixture.execute("""
                    INSERT INTO wm_stock_document_line
                    SELECT 100,document_id,stock_action_id,batch_no,bin_code,quantity,material_id,unit_id,warehouse_id,stock_id
                    FROM wm_stock_document_line WHERE id=1
                    """);
            assertEquals("正式生产入库明细与库存动作不一致", fixture.service.blockedReason(1L));
            verify(fixture.quality, never()).receiptBlockReason(anyString());
        }
    }

    @Test
    void perExecutionUnderAndOverReceiptCannotCancelEachOtherOut() throws Exception {
        try (var fixture = new Fixture(2)) {
            fixture.changeQuantity(1, new BigDecimal("5"));
            fixture.changeQuantity(3, new BigDecimal("7"));
            assertEquals("执行正常品入库与有效产出不一致", fixture.service.blockedReason(1L));
            verify(fixture.quality, never()).receiptBlockReason(anyString());
        }
    }

    @Test
    void anAllDefectExecutionDoesNotNeedAZeroQuantityNormalReceipt() throws Exception {
        try (var fixture = new Fixture(1)) {
            fixture.execute("DELETE FROM wm_stock_document");
            fixture.rows.getFirst().setRoutingId(11L);
            var node = new NodeQuantity("R2-E-1", 11L, 1L, "N", "工序", 1, BigDecimal.TEN, BigDecimal.TEN,
                    BigDecimal.TEN, BigDecimal.ZERO, BigDecimal.ZERO, true);
            fixture.projections.put("R2-E-1", new ExecutionQuantity("R2-E-1", 1L, 11L, BigDecimal.TEN, "LOCKED",
                    false, null, List.of(node), null, BigDecimal.TEN, BigDecimal.TEN, BigDecimal.ZERO, 1L));
            assertNull(fixture.service.blockedReason(1L));
            assertEquals(1, fixture.counter.total());
        }
    }

    @Test
    void batchMetricsMatchSingleCodeReadsAcrossValidMissingAndCorruptedFacts() throws Exception {
        try (var fixture = new Fixture(10)) {
            fixture.execute("DELETE FROM wm_stock_document_line WHERE id=3");
            fixture.execute("DELETE FROM wm_stock_action WHERE id='action-5'");
            fixture.execute("UPDATE wm_stock_action SET request_hash='wrong' WHERE id='action-7'");
            fixture.execute("UPDATE wm_stock_action SET batch_no='batch-001' WHERE id='action-9'");
            fixture.execute("UPDATE wm_stock_document SET status='REVERSED' WHERE id IN (11,12)");
            fixture.execute("DELETE FROM mes_production_execution WHERE id=7");
            fixture.execute("UPDATE wm_stock_document_line SET quantity=NULL WHERE id=15");
            fixture.execute("UPDATE wm_stock_document_line SET warehouse_id=NULL WHERE id=17");
            fixture.execute("UPDATE wm_stock_document_line SET bin_code='Bin-A' WHERE id=19");
            var codes = List.of("R2-E-10", "R2-E-9", "R2-E-8", "R2-E-7", "R2-E-6", "R2-E-5",
                    "R2-E-4", "R2-E-3", "R2-E-2", "R2-E-1", "NO-DOCUMENT");
            assertEquivalent(fixture, codes);
        }
    }

    @Test
    void binaryExecutionKeysKeepCaseVariantsDistinctAndPreserveTheFullCode() throws Exception {
        try (var fixture = new Fixture(2)) {
            fixture.changeCode("R2-E-2", "r2-e-1");
            fixture.execute("ALTER TABLE wm_stock_document ALTER COLUMN source_execution_code VARBINARY(2048)");
            fixture.execute("ALTER TABLE wm_stock_action ALTER COLUMN execution_code VARBINARY(2048)");
            fixture.execute("ALTER TABLE mes_production_execution ALTER COLUMN execution_code VARBINARY(2048)");
            assertEquivalent(fixture, List.of("R2-E-1", "r2-e-1"));
            String code = "工单:" + "物料#原料001$".repeat(40);
            fixture.changeCode("R2-E-1", code);
            assertEquivalent(fixture, List.of(code, "r2-e-1"));
        }
    }

    @Test
    void databaseFailureIsNotConvertedToZeroReceipts() throws Exception {
        try (var fixture = new Fixture(1)) {
            fixture.execute("DROP TABLE wm_stock_action");
            assertThrows(org.apache.ibatis.exceptions.PersistenceException.class,
                    () -> fixture.service.blockedReason(1L));
            verify(fixture.quality, never()).receiptBlockReason(anyString());
        }
    }

    @Test
    void downstreamQualityFailuresStillPropagateUnchanged() throws Exception {
        try (var fixture = new Fixture(1)) {
            var failure = new IllegalStateException("TEST ONLY quality read failure");
            when(fixture.quality.receiptBlockReason("R2-E-1")).thenThrow(failure);
            assertSame(failure, assertThrows(IllegalStateException.class, () -> fixture.service.blockedReason(1L)));
        }
    }

    private static ExecutionQuantity missingInput(String code) {
        return new ExecutionQuantity(code, 1L, null, BigDecimal.TEN, null, false, null, List.of(), null,
                BigDecimal.TEN, BigDecimal.ZERO, BigDecimal.ZERO, 1L);
    }

    private static void assertEquivalent(Fixture fixture, List<String> codes) {
        var batch = fixture.inbound.productionInboundSummaries(codes);
        var byCode = new HashMap<String, StockDocumentExtraMapper.ProductionInboundSummary>();
        for (var row : batch) {
            assertTrue(codes.contains(row.executionCode()));
            assertNull(byCode.put(row.executionCode(), row));
        }
        for (String code : codes) {
            var row = byCode.getOrDefault(code,
                    new StockDocumentExtraMapper.ProductionInboundSummary(code, BigDecimal.ZERO, 0));
            assertEquals(0, fixture.inbound.formalProductionInbound(code).compareTo(row.receivedQty()));
            assertEquals(fixture.inbound.invalidProductionInboundLines(code), row.invalidCount());
        }
    }

    @Intercepts(@Signature(type = Executor.class, method = "query", args = {
            MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class}))
    public static class QueryCounter implements Interceptor {
        final Map<String, Integer> calls = new LinkedHashMap<>();

        int total() {
            return calls.values().stream().mapToInt(Integer::intValue).sum();
        }

        @Override
        public Object intercept(Invocation invocation) throws Throwable {
            String id = ((MappedStatement) invocation.getArgs()[0]).getId();
            if (id.startsWith(StockDocumentExtraMapper.class.getName() + "."))
                calls.merge(id.substring(id.lastIndexOf('.') + 1), 1, Integer::sum);
            return invocation.proceed();
        }
    }

    private static final class Fixture implements AutoCloseable {
        final SqlSession session;
        final QueryCounter counter = new QueryCounter();
        final ProductionQuantityService quantities = mock(ProductionQuantityService.class);
        final QualityTreatmentService quality = mock(QualityTreatmentService.class);
        final WorkorderMapper orders = mock(WorkorderMapper.class);
        final WorkOrderExtraMapper queries = mock(WorkOrderExtraMapper.class);
        final MesProductionExecutionMapper executions = mock(MesProductionExecutionMapper.class);
        final List<MesProductionExecutionEntity> rows = new ArrayList<>();
        final Map<String, ExecutionQuantity> projections = new HashMap<>();
        final StockDocumentExtraMapper inbound;
        final WorkOrderCompletionService service;

        Fixture(int count) throws Exception {
            String url = "jdbc:h2:mem:r2_" + UUID.randomUUID() + ";MODE=MySQL";
            var configuration = new Configuration(new Environment("R2 isolated H2", new JdbcTransactionFactory(),
                    new UnpooledDataSource("org.h2.Driver", url, null)));
            configuration.setCacheEnabled(false);
            configuration.setLocalCacheScope(LocalCacheScope.STATEMENT);
            configuration.addInterceptor(counter);
            configuration.addMapper(ProductionDefectListExtraMapper.class);
            configuration.addMapper(WmStockDocumentMapper.class);
            configuration.addMapper(StockDocumentExtraMapper.class);
            session = new SqlSessionFactoryBuilder().build(configuration).openSession();
            try {
                try (var statement = session.getConnection().createStatement()) {
                    for (String sql : SCHEMA) statement.execute(sql);
                    statement.execute("INSERT INTO workorder VALUES(1,1,'LOCAL')");
                    statement.execute("INSERT INTO material VALUES(1,1)");
                    statement.execute("INSERT INTO wm_warehouse VALUES(1,'LOCAL')");
                }
                seed(count);
                session.commit();
            } catch (Exception failure) {
                session.close();
                throw failure;
            }
            inbound = session.getMapper(StockDocumentExtraMapper.class);
            var order = new WorkorderEntity();
            order.setId(1L);
            order.setSource("LOCAL");
            order.setStatus("RELEASED");
            BigDecimal planned = BigDecimal.TEN.multiply(BigDecimal.valueOf(count));
            order.setPlannedQty(planned);
            when(orders.selectByPrimaryKey(1L)).thenReturn(order);
            when(queries.completionState(1L)).thenReturn(new CompletionState(count, 0, 0));
            when(queries.activeAllocation(1L, null)).thenReturn(new Allocation(1L, planned, planned,
                    BigDecimal.ZERO, BigDecimal.ZERO, (long) count));
            when(executions.selectByExample(any())).thenAnswer(invocation -> {
                MesProductionExecutionExample filter = invocation.getArgument(0);
                long after = (Long) filter.getOredCriteria().getFirst().getCriteria().stream()
                        .filter(c -> c.getCondition().equals("id >")).findFirst().orElseThrow().getValue();
                int limit = PageHelper.getLocalPage().getPageSize();
                PageHelper.clearPage(); // Simulate the pagination interceptor's cleanup for this mocked Mapper.
                return rows.stream().filter(e -> e.getId() > after).limit(limit).toList();
            });
            when(quantities.getExecutionQuantities(anyString()))
                    .thenAnswer(invocation -> projections.get(invocation.getArgument(0)));
            var issues = mock(WmStockDocumentMapper.class);
            when(issues.selectByExample(any())).thenReturn(List.of());
            service = new WorkOrderCompletionService(orders, queries, executions, issues, quantities, quality,
                    mock(BusinessSourceRules.class), inbound);
        }

        private void seed(int count) throws SQLException {
            try (var execution = session.getConnection().prepareStatement(
                    "INSERT INTO mes_production_execution VALUES(?,?,1,1,'Batch-001')");
                 var document = session.getConnection().prepareStatement("""
                         INSERT INTO wm_stock_document VALUES(?,?,'POSTED','PRODUCTION_RECEIPT',NULL,?,?,1,
                         TIMESTAMP '2026-01-01 00:00:00')
                         """);
                 var line = session.getConnection().prepareStatement("""
                         INSERT INTO wm_stock_document_line VALUES(?,?,?,'Batch-001',NULL,?,1,1,1,?)
                         """);
                 var action = session.getConnection().prepareStatement("""
                         INSERT INTO wm_stock_action VALUES(?,?,?,?,'OTHER_RECEIPT','OTHER_RECEIPT',?,NULL,?,
                         1,1,1,'Batch-001',NULL,?,?,0,?,0,?,1,?)
                         """)) {
                for (int id = 1; id <= count; id++) {
                    String code = "R2-E-" + id;
                    var row = new MesProductionExecutionEntity();
                    row.setId((long) id);
                    row.setExecutionCode(code);
                    row.setStatus("FULLY_RECEIVED");
                    row.setWorkorderId(1L);
                    row.setProductId(1L);
                    row.setTargetQty(BigDecimal.TEN);
                    rows.add(row);
                    projections.put(code, new ExecutionQuantity(code, 1L, null, BigDecimal.TEN, null, false,
                            null, List.of(), null, BigDecimal.TEN, BigDecimal.TEN, BigDecimal.ZERO, 1L));
                    execution.setInt(1, id);
                    execution.setString(2, code);
                    execution.addBatch();
                    for (int part = 0; part < 2; part++) {
                        int documentId = id * 2 - 1 + part;
                        BigDecimal quantity = new BigDecimal(part == 0 ? "6" : "4");
                        String actionId = "action-" + documentId;
                        String request = "request-" + documentId;
                        String hash = "hash-" + documentId;
                        document.setInt(1, documentId);
                        document.setString(2, code);
                        document.setString(3, request);
                        document.setString(4, hash);
                        document.addBatch();
                        line.setInt(1, documentId);
                        line.setInt(2, documentId);
                        line.setString(3, actionId);
                        line.setBigDecimal(4, quantity);
                        line.setInt(5, documentId);
                        line.addBatch();
                        action.setString(1, actionId);
                        action.setInt(2, documentId);
                        action.setString(3, code);
                        action.setString(4, request);
                        action.setString(5, Integer.toString(documentId));
                        action.setInt(6, documentId);
                        for (int position = 7; position <= 10; position++) action.setBigDecimal(position, quantity);
                        action.setString(11, hash);
                        action.addBatch();
                    }
                }
                execution.executeBatch();
                document.executeBatch();
                line.executeBatch();
                action.executeBatch();
            }
        }

        void execute(String sql) throws SQLException {
            try (var statement = session.getConnection().createStatement()) {
                statement.execute(sql);
            }
            session.clearCache();
        }

        void changeQuantity(int id, BigDecimal quantity) throws SQLException {
            try (var line = session.getConnection().prepareStatement(
                    "UPDATE wm_stock_document_line SET quantity=? WHERE id=?");
                 var action = session.getConnection().prepareStatement("""
                         UPDATE wm_stock_action SET quantity_delta=?,available_delta=?,quantity_after=?,available_after=?
                         WHERE document_line_id=?
                         """)) {
                line.setBigDecimal(1, quantity);
                line.setInt(2, id);
                line.executeUpdate();
                for (int i = 1; i <= 4; i++) action.setBigDecimal(i, quantity);
                action.setInt(5, id);
                action.executeUpdate();
            }
            session.clearCache();
        }

        void changeCode(String original, String replacement) throws SQLException {
            for (String table : List.of("mes_production_execution", "wm_stock_action", "wm_stock_document")) {
                String column = table.equals("wm_stock_document") ? "source_execution_code" : "execution_code";
                try (var statement = session.getConnection()
                        .prepareStatement("UPDATE " + table + " SET " + column + "=? WHERE " + column + "=?")) {
                    statement.setString(1, replacement);
                    statement.setString(2, original);
                    statement.executeUpdate();
                }
            }
            session.clearCache();
        }

        String fingerprint() throws Exception {
            var digest = MessageDigest.getInstance("SHA-256");
            for (String table : List.of("mes_production_execution", "wm_stock_document", "wm_stock_document_line",
                    "wm_stock_action", "workorder", "material", "wm_warehouse")) {
                digest.update(table.getBytes(StandardCharsets.UTF_8));
                try (var statement = session.getConnection().createStatement();
                     var result = statement.executeQuery("SELECT * FROM " + table + " ORDER BY id")) {
                    while (result.next()) {
                        for (int column = 1; column <= result.getMetaData().getColumnCount(); column++) {
                            String value = result.getString(column);
                            if (value == null) digest.update((byte) 0);
                            else digest.update(value.getBytes(StandardCharsets.UTF_8));
                            digest.update((byte) 255);
                        }
                    }
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        }

        @Override
        public void close() {
            session.close();
            PageHelper.clearPage();
        }
    }

    private static final List<String> SCHEMA = List.of(
            "CREATE TABLE workorder(id BIGINT PRIMARY KEY,product_id BIGINT,source VARCHAR(16))",
            "CREATE TABLE material(id BIGINT PRIMARY KEY,unit_id BIGINT)",
            "CREATE TABLE wm_warehouse(id BIGINT PRIMARY KEY,source VARCHAR(16))",
            """
                    CREATE TABLE mes_production_execution(id BIGINT PRIMARY KEY,execution_code VARCHAR(512),workorder_id BIGINT,
                    product_id BIGINT,batch_no VARCHAR(128))
                    """,
            """
                    CREATE TABLE wm_stock_document(id BIGINT PRIMARY KEY,source_execution_code VARCHAR(512),status VARCHAR(16),
                    document_type VARCHAR(40),source_workorder_id BIGINT,post_request_id VARCHAR(64),
                    post_request_hash VARCHAR(64),posted_by BIGINT,posted_at TIMESTAMP)
                    """,
            "CREATE INDEX doc_execution ON wm_stock_document(source_execution_code,status,id)",
            """
                    CREATE TABLE wm_stock_document_line(id BIGINT PRIMARY KEY,document_id BIGINT,stock_action_id VARCHAR(64),
                    batch_no VARCHAR(128),bin_code VARCHAR(64),quantity DECIMAL(20,6),material_id BIGINT,unit_id BIGINT,
                    warehouse_id BIGINT,stock_id BIGINT)
                    """,
            "CREATE INDEX line_document ON wm_stock_document_line(document_id)",
            """
                    CREATE TABLE wm_stock_action(id VARCHAR(64) PRIMARY KEY,document_line_id BIGINT,execution_code VARCHAR(512),
                    operation_id VARCHAR(64),action_type VARCHAR(40),source_type VARCHAR(40),source_id VARCHAR(64),
                    reference_action_id VARCHAR(64),stock_id BIGINT,material_id BIGINT,unit_id BIGINT,warehouse_id BIGINT,
                    batch_no VARCHAR(128),bin_code VARCHAR(64),quantity_delta DECIMAL(20,6),available_delta DECIMAL(20,6),
                    quantity_before DECIMAL(20,6),quantity_after DECIMAL(20,6),available_before DECIMAL(20,6),
                    available_after DECIMAL(20,6),operator_user_id BIGINT,request_hash VARCHAR(64))
                    """);
}
