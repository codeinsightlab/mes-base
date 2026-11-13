package dev.mesbase.business.wm.service;

import dev.mesbase.business.production.dao.ManufacturedInputSourceExtraMapper;
import dev.mesbase.business.production.dao.ProductionInputExtraMapper;
import dev.mesbase.business.quality.dao.ProductionDefectListExtraMapper;
import dev.mesbase.business.wm.dao.StockDocumentExtraMapper;
import dev.mesbase.business.wm.dao.WmStockDocumentMapper;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Real Mapper SQL against an isolated, case-insensitive H2 fixture; never a shared MySQL connection.
 */
class StockIdentityMapperSqlTest {
    private enum Projection {NORMAL, QUALITY, INPUT, SOURCE}

    private enum Field {
        BATCH("batch_no"), BIN("bin_code");

        private final String column;

        Field(String column) {
            this.column = column;
        }
    }

    private record Link(Projection projection, String table, String predicate, boolean hasBin) {
    }

    private record TextPair(String label, String left, String right) {
    }

    private static Stream<Arguments> identitySamples() {
        var links = List.of(
                new Link(Projection.NORMAL, "wm_stock_action", "id='normal-action'", true),
                new Link(Projection.NORMAL, "mes_production_execution", "execution_code='E'", false),
                new Link(Projection.QUALITY, "wm_stock_action", "id='quality-action'", true),
                new Link(Projection.QUALITY, "mes_production_execution", "execution_code='E'", false),
                new Link(Projection.INPUT, "wm_stock_action", "id='issue-action'", true),
                new Link(Projection.INPUT, "wm_stock_action", "id='return-action'", true),
                new Link(Projection.SOURCE, "wm_stock_action", "id='normal-action'", true),
                new Link(Projection.SOURCE, "mes_production_execution", "execution_code='E'", false),
                new Link(Projection.SOURCE, "wm_inventory", "id=100", true));
        var pairs = List.of(
                new TextPair("identical", "Lot-001", "Lot-001"),
                new TextPair("unicode-identical", "批次-é-001", "批次-é-001"),
                new TextPair("case", "Lot-A", "lot-a"),
                new TextPair("suffix-case", "Lot-A", "Lot-a"),
                new TextPair("long-prefix", "物".repeat(63) + "A", "物".repeat(63) + "a"),
                new TextPair("accent", "Café", "Cafe"),
                new TextPair("unicode-normalization", "é", "e\u0301"),
                new TextPair("trailing-space", "Lot-A", "Lot-A "),
                new TextPair("leading-zero", "001", "1"),
                new TextPair("null-null", null, null),
                new TextPair("null-empty", null, ""),
                new TextPair("empty-null", "", null),
                new TextPair("empty-empty", "", ""),
                new TextPair("null-text", null, "Lot-A"),
                new TextPair("text-null", "Lot-A", null),
                new TextPair("binary-padding", "Lot-A", "Lot-A\u0000"));
        return links.stream().flatMap(link -> Stream.of(Field.values())
                .filter(field -> field != Field.BIN || link.hasBin())
                .flatMap(field -> pairs.stream().map(pair -> Arguments.of(link, field, pair))));
    }

    @ParameterizedTest(name = "{0} {1} {2}")
    @MethodSource("identitySamples")
    void mappedIdentityMatchesStockIdentityWithoutRelaxingRequiredBatch(Link link, Field field, TextPair pair)
            throws Exception {
        try (var session = database()) {
            setAllText(session, field, pair.left());
            setText(session, link.table(), link.predicate(), field, pair.right());
            boolean expected = identity(field, pair.left()).equals(identity(field, pair.right()));
            // Production/quality receipts and manufactured sources already require a non-null output batch.
            if (field == Field.BATCH && link.projection() != Projection.INPUT && pair.left() == null)
                expected = false;
            assertProjection(session, link.projection(), expected);
        }
    }

    @Test
    void fixtureActuallyHasCaseInsensitiveTextAndOriginalTextIsNotRewritten() throws Exception {
        try (var session = database()) {
            setText(session, "wm_stock_action", "id='normal-action'", Field.BIN, "bin-a");
            try (var statement = session.getConnection().createStatement();
                 var rows = statement.executeQuery("""
                         SELECT a.bin_code=l.bin_code AS loose_match,a.bin_code AS action_bin,l.bin_code AS line_bin
                         FROM wm_stock_action a JOIN wm_stock_document_line l ON l.stock_action_id=a.id
                         WHERE a.id='normal-action'
                         """)) {
                assertTrue(rows.next());
                assertTrue(rows.getBoolean("loose_match"));
                assertEquals("bin-a", rows.getString("action_bin"));
                assertEquals("Bin-A", rows.getString("line_bin"));
            }
            assertProjection(session, Projection.NORMAL, false);
        }
    }

    @Test
    void latestAuditFallsBackOnlyToOtherExactProofs() throws Exception {
        try (var session = database()) {
            var mapper = session.getMapper(StockDocumentExtraMapper.class);
            assertEquals("quality-request", mapper.latestInboundAuditId("E"));
            setText(session, "wm_stock_action", "id='quality-action'", Field.BIN, "bin-a");
            assertEquals("normal-request", mapper.latestInboundAuditId("E"));
            setText(session, "wm_stock_action", "id='normal-action'", Field.BIN, "bin-a");
            assertNull(mapper.latestInboundAuditId("E"));
        }
    }

    @Test
    void partialReceiptsStillSumAndReversedDocumentsRemainExcluded() throws Exception {
        try (var session = database(); var statement = session.getConnection().createStatement()) {
            statement.execute("""
                    INSERT INTO wm_stock_document
                    SELECT 11,'normal-2',document_type,status,direction,source_execution_code,source_workorder_id,
                           'normal-request-2',post_request_hash,posted_by,posted_at
                    FROM wm_stock_document WHERE id=1
                    """);
            statement.execute("""
                    INSERT INTO wm_stock_document_line
                    SELECT 11,11,stock_id,material_id,unit_id,warehouse_id,batch_no,bin_code,2,'normal-action-2',NULL
                    FROM wm_stock_document_line WHERE id=1
                    """);
            statement.execute("""
                    INSERT INTO wm_stock_action
                    SELECT 'normal-action-2',11,'normal-request-2',execution_code,action_type,source_type,'11',
                           reference_action_id,stock_id,material_id,unit_id,warehouse_id,batch_no,bin_code,
                           2,2,10,12,10,12,operator_user_id,request_hash
                    FROM wm_stock_action WHERE id='normal-action'
                    """);
            var mapper = session.getMapper(StockDocumentExtraMapper.class);
            assertEquals(0, new BigDecimal("12").compareTo(mapper.formalProductionInbound("E")));
            assertEquals(0, mapper.invalidProductionInboundLines("E"));
            statement.execute("UPDATE wm_stock_document SET status='REVERSED' WHERE id=1");
            session.clearCache();
            assertEquals(0, new BigDecimal("2").compareTo(mapper.formalProductionInbound("E")));
            assertEquals(0, mapper.invalidProductionInboundLines("E"));
        }
    }

    @Test
    void exactTextDoesNotBypassAuditQuantityOrUniqueActionProof() throws Exception {
        try (var session = database(); var statement = session.getConnection().createStatement()) {
            statement.execute("UPDATE wm_stock_action SET request_hash='wrong' WHERE id='normal-action'");
            assertProjection(session, Projection.NORMAL, false);
            statement.execute("UPDATE wm_stock_action SET request_hash='normal-hash',quantity_delta=9 WHERE id='normal-action'");
            session.clearCache();
            assertProjection(session, Projection.NORMAL, false);
            statement.execute("UPDATE wm_stock_action SET quantity_delta=10 WHERE id='normal-action'");
            statement.execute("INSERT INTO wm_stock_action(id,document_line_id) VALUES('duplicate-action',1)");
            session.clearCache();
            assertProjection(session, Projection.NORMAL, false);
            statement.execute("DELETE FROM wm_stock_action WHERE id='duplicate-action'");
            session.clearCache();
            assertProjection(session, Projection.NORMAL, true);
        }
    }

    @Test
    void returnsStillAggregateByOriginalActionWithoutCountingReversalsAsReturns() throws Exception {
        try (var session = database(); var statement = session.getConnection().createStatement()) {
            statement.execute("""
                    INSERT INTO wm_stock_action
                    SELECT 'return-2',document_line_id,operation_id,execution_code,action_type,source_type,source_id,
                           reference_action_id,stock_id,material_id,unit_id,warehouse_id,batch_no,bin_code,
                           0.5,0.5,quantity_before,quantity_after,available_before,available_after,
                           operator_user_id,request_hash
                    FROM wm_stock_action WHERE id='return-action'
                    """);
            statement.execute("""
                    INSERT INTO wm_stock_action(id,reference_action_id,action_type,quantity_delta)
                    VALUES('reversal','issue-action','REVERSAL',2.5)
                    """);
            var mapper = session.getMapper(ProductionInputExtraMapper.class);
            var row = mapper.executionMaterialBalances(List.of("E")).getFirst();
            assertEquals(0, new BigDecimal("1.5").compareTo(row.returnedQty()));
            assertEquals(2, row.returnCount());
            assertEquals(0, row.invalidCount());
            statement.execute("UPDATE wm_stock_document SET status='REVERSED' WHERE id=3");
            session.clearCache();
            assertTrue(mapper.executionMaterialBalances(List.of("E")).isEmpty());
        }
    }

    private StockActionRuleRegistry.StockIdentity identity(Field field, String text) {
        String batch = "Lot-001", bin = "Bin-A";
        if (field == Field.BATCH) batch = text;
        else bin = text;
        return new StockActionRuleRegistry.StockIdentity(100L, 1L, 1L, 1L, batch, bin);
    }

    private void assertProjection(SqlSession session, Projection projection, boolean valid) {
        switch (projection) {
            case NORMAL -> {
                var mapper = session.getMapper(StockDocumentExtraMapper.class);
                assertEquals(0, new BigDecimal(valid ? "10" : "0").compareTo(mapper.formalProductionInbound("E")));
                assertEquals(valid ? 0 : 1, mapper.invalidProductionInboundLines("E"));
            }
            case QUALITY -> {
                var mapper = session.getMapper(ProductionDefectListExtraMapper.class);
                var rows = mapper.selectReceiptProofs("E");
                assertEquals(1, rows.size());
                assertEquals(valid ? 1 : 0, rows.getFirst().validCount());
                assertEquals(valid ? 0 : 1, mapper.invalidCurrentReceipts("E"));
            }
            case INPUT -> {
                var rows = session.getMapper(ProductionInputExtraMapper.class).executionMaterialBalances(List.of("E"));
                assertEquals(1, rows.size());
                assertEquals(valid, rows.getFirst().invalidCount() == 0);
                assertEquals(0, new BigDecimal("4").compareTo(rows.getFirst().issuedQty()));
                assertEquals(0, BigDecimal.ONE.compareTo(rows.getFirst().returnedQty()));
            }
            case SOURCE -> {
                var rows = session.getMapper(ManufacturedInputSourceExtraMapper.class)
                        .selectSources(1L, 1L, 1L, List.of(1L));
                assertEquals(valid ? 1 : 0, rows.size());
                if (valid) assertEquals(0, BigDecimal.TEN.compareTo(rows.getFirst().receivedQty()));
            }
        }
    }

    private void setAllText(SqlSession session, Field field, String text) throws SQLException {
        for (String table : List.of("wm_stock_action", "wm_stock_document_line", "wm_inventory"))
            setText(session, table, "1=1", field, text);
        if (field == Field.BATCH) setText(session, "mes_production_execution", "1=1", field, text);
    }

    private void setText(SqlSession session, String table, String predicate, Field field, String text)
            throws SQLException {
        // Identifiers and predicates come only from this test's fixed Link/Field definitions.
        try (var statement = session.getConnection()
                .prepareStatement("UPDATE " + table + " SET " + field.column + "=? WHERE " + predicate)) {
            statement.setString(1, text);
            statement.executeUpdate();
        }
        session.clearCache();
    }

    private SqlSession database() throws SQLException {
        String url = "jdbc:h2:mem:r1_" + UUID.randomUUID() + ";MODE=MySQL";
        var configuration = new Configuration(new Environment("R1 isolated case-insensitive fixture",
                new JdbcTransactionFactory(), new UnpooledDataSource("org.h2.Driver", url, null)));
        configuration.addMapper(ProductionDefectListExtraMapper.class);
        configuration.addMapper(WmStockDocumentMapper.class);
        configuration.addMapper(StockDocumentExtraMapper.class);
        configuration.addMapper(ProductionInputExtraMapper.class);
        configuration.addMapper(ManufacturedInputSourceExtraMapper.class);
        var session = new SqlSessionFactoryBuilder().build(configuration).openSession();
        try (var statement = session.getConnection().createStatement()) {
            for (String sql : SCHEMA_AND_FACTS) statement.execute(sql);
        } catch (SQLException failure) {
            session.close();
            throw failure;
        }
        return session;
    }

    // TEST ONLY: schema subset for real query execution, not product DDL or a MySQL collation simulation.
    private static final List<String> SCHEMA_AND_FACTS = List.of(
            """
                    CREATE TABLE wm_stock_document(id BIGINT, document_no VARCHAR(64), document_type VARCHAR(40), status
                    VARCHAR(20), direction VARCHAR(8), source_execution_code VARCHAR(64), source_workorder_id BIGINT,
                    post_request_id VARCHAR(64), post_request_hash VARCHAR(64), posted_by BIGINT, posted_at TIMESTAMP)
                    """,
            """
                    CREATE TABLE wm_stock_document_line(id BIGINT, document_id BIGINT, stock_id BIGINT, material_id BIGINT,
                    unit_id BIGINT, warehouse_id BIGINT, batch_no VARCHAR_IGNORECASE(128), bin_code VARCHAR_IGNORECASE(64),
                    quantity DECIMAL(20, 6), stock_action_id VARCHAR(64), demand_line_id BIGINT)
                    """,
            """
                    CREATE TABLE wm_stock_action(id VARCHAR(64), document_line_id BIGINT, operation_id VARCHAR(64),
                    execution_code VARCHAR(64), action_type VARCHAR(40), source_type VARCHAR(40), source_id VARCHAR(64),
                    reference_action_id VARCHAR(64), stock_id BIGINT, material_id BIGINT, unit_id BIGINT, warehouse_id BIGINT,
                    batch_no VARCHAR_IGNORECASE(128), bin_code VARCHAR_IGNORECASE(64), quantity_delta DECIMAL(20, 6),
                    available_delta DECIMAL(20, 6), quantity_before DECIMAL(20, 6), quantity_after DECIMAL(20, 6),
                    available_before DECIMAL(20, 6), available_after DECIMAL(20, 6), operator_user_id BIGINT, request_hash
                    VARCHAR(64))
                    """,
            """
                    CREATE TABLE mes_production_execution(id BIGINT, execution_code VARCHAR(64), workorder_id BIGINT,
                    product_id BIGINT, batch_no VARCHAR_IGNORECASE(128))
                    """,
            """
                    CREATE TABLE workorder(id BIGINT, product_id BIGINT, source VARCHAR(16), bom_id BIGINT, material_frozen_at
                    TIMESTAMP)
                    """,
            """
                    CREATE TABLE material(id BIGINT, unit_id BIGINT, status VARCHAR(16))
                    """,
            """
                    CREATE TABLE wm_warehouse(id BIGINT, source VARCHAR(16), warehouse_type VARCHAR(32), status VARCHAR(16))
                    """,
            """
                    CREATE TABLE unit_measure(id BIGINT, status VARCHAR(16), must_be_whole_number INT, quantity_precision INT)
                    """,
            """
                    CREATE TABLE wm_inventory(id BIGINT, material_id BIGINT, warehouse_id BIGINT, batch_no
                    VARCHAR_IGNORECASE(128), bin_code VARCHAR_IGNORECASE(64), quantity DECIMAL(20, 6), available_quantity
                    DECIMAL(20, 6), status VARCHAR(16))
                    """,
            """
                    CREATE TABLE material_bom(id BIGINT, product_id BIGINT, unit_id BIGINT)
                    """,
            """
                    CREATE TABLE mes_workorder_material(id BIGINT, workorder_id BIGINT, material_id BIGINT, unit_id BIGINT,
                    total_required_qty DECIMAL(20, 6), planned_qty DECIMAL(20, 6))
                    """,
            """
                    CREATE TABLE mes_material_issue_input(document_id BIGINT, execution_code VARCHAR(64), input_kind
                    VARCHAR(40), confirmed_at TIMESTAMP, stock_applied BOOLEAN, source_mode VARCHAR(40), output_qty
                    DECIMAL(20, 6))
                    """,
            """
                    CREATE TABLE mes_production_defect(id BIGINT, declaration_id VARCHAR(64), revision_no INT, execution_code
                    VARCHAR(64), record_action VARCHAR(16))
                    """,
            """
                    CREATE TABLE mes_quality_treatment(id VARCHAR(64), declaration_id VARCHAR(64), declaration_revision INT,
                    execution_code VARCHAR(64), action_type VARCHAR(40), target_warehouse_id BIGINT, transfer_qty DECIMAL(20,
                    6), returned_qty DECIMAL(20, 6), stock_document_id BIGINT, operator_user_id BIGINT)
                    """,
            """
                    INSERT INTO workorder VALUES(1, 1, 'LOCAL' , 1, CURRENT_TIMESTAMP)
                    """,
            """
                    INSERT INTO material VALUES(1, 1, 'ENABLED' ), (2, 1, 'ENABLED' )
                    """,
            """
                    INSERT INTO unit_measure VALUES(1, 'ENABLED' , 0, 6)
                    """,
            """
                    INSERT INTO wm_warehouse VALUES(1, 'LOCAL' , 'NORMAL' , 'ENABLED' ), (2, 'LOCAL' , 'DEFECTIVE' , 'ENABLED'
                    )
                    """,
            """
                    INSERT INTO material_bom VALUES(1, 1, 1)
                    """,
            """
                    INSERT INTO mes_production_execution VALUES(1, 'E' , 1, 1, 'Lot-001' )
                    """,
            """
                    INSERT INTO wm_stock_document VALUES(1, 'normal' , 'PRODUCTION_RECEIPT' , 'POSTED' , 'IN' , 'E' , NULL,
                    'normal-request' , 'normal-hash' , 7, '2026-01-01 00:00:00' ), (2, 'quality' , 'DEFECT_RECEIPT' , 'POSTED'
                    , 'IN' , NULL, NULL, 'quality-request' , 'quality-hash' , 7, '2026-01-02 00:00:00' ), (3, 'issue' ,
                    'MATERIAL_ISSUE' , 'POSTED' , 'OUT' , 'E' , 1, 'issue-request' , 'issue-hash' , 7, '2026-01-01 00:00:00' )
                    """,
            """
                    INSERT INTO wm_stock_document_line VALUES(1, 1, 100, 1, 1, 1, 'Lot-001' , 'Bin-A' , 10, 'normal-action' ,
                    NULL), (2, 2, 200, 1, 1, 2, 'Lot-001' , 'Bin-A' , 3, 'quality-action' , NULL), (3, 3, 300, 2, 1, 1,
                    'Lot-001' , 'Bin-A' , 4, 'issue-action' , 1)
                    """,
            """
                    INSERT INTO wm_inventory VALUES(100, 1, 1, 'Lot-001' , 'Bin-A' , 10, 10, 'ENABLED' ), (200, 1, 2,
                    'Lot-001' , 'Bin-A' , 3, 3, 'ENABLED' ), (300, 2, 1, 'Lot-001' , 'Bin-A' , 6, 6, 'ENABLED' )
                    """,
            """
                    INSERT INTO wm_stock_action VALUES( 'normal-action' , 1, 'normal-request' , 'E' , 'OTHER_RECEIPT' ,
                    'OTHER_RECEIPT' , '1' , NULL, 100, 1, 1, 1, 'Lot-001' , 'Bin-A' , 10, 10, 0, 10, 0, 10, 7, 'normal-hash'
                    ), ( 'quality-action' , 2, 'quality-request' , 'E' , 'OTHER_RECEIPT' , 'OTHER_RECEIPT' , '2' , NULL, 200,
                    1, 1, 2, 'Lot-001' , 'Bin-A' , 3, 3, 0, 3, 0, 3, 7, 'quality-hash' ), ( 'issue-action' , 3,
                    'issue-request' , 'E' , 'ISSUE' , 'MATERIAL_ISSUE' , '3' , NULL, 300, 2, 1, 1, 'Lot-001' , 'Bin-A' , -4,
                    -4, 10, 6, 10, 6, 7, 'issue-hash' ), ( 'return-action' , NULL, 'return-request' , 'E' , 'RETURN' ,
                    'MATERIAL_ISSUE' , '3' , 'issue-action' , 300, 2, 1, 1, 'Lot-001' , 'Bin-A' , 1, 1, 6, 7, 6, 7, 7,
                    'return-hash' )
                    """,
            """
                    INSERT INTO mes_workorder_material VALUES(1, 1, 2, 1, 4, 10)
                    """,
            """
                    INSERT INTO mes_material_issue_input VALUES(3, 'E' , 'PRODUCTION_INPUT' , CURRENT_TIMESTAMP, TRUE,
                    'LOCAL_ONLY' , 10)
                    """,
            """
                    INSERT INTO mes_production_defect VALUES(1, 'declaration' , 1, 'E' , 'CREATE' )
                    """,
            """
                    INSERT INTO mes_quality_treatment VALUES( 'quality-request' , 'declaration' , 1, 'E' ,
                    'TRANSFER_WAREHOUSE' , 2, 3, 0, 2, 7)
                    """);
}
