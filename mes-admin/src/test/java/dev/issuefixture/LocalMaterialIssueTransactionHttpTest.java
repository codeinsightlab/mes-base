package dev.issuefixture;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import dev.mesbase.business.directory.service.BusinessDirectoryService;
import dev.mesbase.business.quality.dao.ProductionDefectListExtraMapper;
import dev.mesbase.business.wm.dao.StockDocumentExtraMapper;
import dev.mesbase.core.factory.FactoryId;
import dev.mesbase.core.factory.FactoryScope;
import dev.mesbase.platform.execution.FactoryContext;
import tools.jackson.databind.ObjectMapper;

import static dev.issuefixture.ProductionCoreV1HttpTest.full;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

/**
 * TEST ONLY: isolated H2 checks actual LOCAL commands and transaction rollback.
 */
class LocalMaterialIssueTransactionHttpTest {
    @Test
    void materialReturnsUseProductionEntryAndLaterReversalOffsetsOnlyTheUnreturnedQuantity() throws Exception {
        try (var context = ProductionInputHttpTest.context()) {
            var mvc = MaterialIssueHttpTest.mvc(context);
            var db = ProductionInputHttpTest.db(context);
            var result = new ObjectMapper().readTree(ProductionInputHttpTest.submit(mvc,
                    ProductionInputHttpTest.command(mvc, UUID.randomUUID().toString(), "L001", "10")));
            String documentId = result.get("documentId").asText();
            var detail = new ObjectMapper().readTree(mvc.perform(full(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(
                    StockDocumentHttpTest.OUT_ROOT + "/" + documentId))).andExpect(status().isOk()).andReturn()
                    .getResponse().getContentAsString());
            String lineId = detail.get("lines").get(0).get("id").asText();
            String returnCommand = "{\"requestId\":\"" + UUID.randomUUID() + "\",\"stockId\":\"1\","
                    + "\"quantity\":\"2\",\"expectedQuantity\":\"9980\",\"expectedAvailable\":\"9980\","
                    + "\"reason\":\"TEST ONLY return before production\",\"documentId\":\"" + documentId
                    + "\",\"documentLineId\":\"" + lineId + "\"}";
            mvc.perform(full(post("/api/wm/local-stock/return")).content(returnCommand))
                    .andExpect(status().isNotFound());
            var returned = new ObjectMapper().readTree(mvc.perform(full(post(ProductionInputHttpTest.ROOT
                    + "/documents/" + documentId + "/returns")).content(returnCommand)).andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString());
            assertEquals("RETURN", returned.get("actionType").asText());
            assertEquals(detail.get("lines").get(0).get("stockActionId").asText(), returned.get("referenceActionId").asText());
            assertEquals(0, new BigDecimal("9982").compareTo(db.queryForObject(
                    "SELECT quantity FROM wm_inventory WHERE id=1", BigDecimal.class)));

            String reverseCommand = StockDocumentHttpTest.cancellationBody(mvc, documentId);
            mvc.perform(full(post(StockDocumentHttpTest.OUT_ROOT + "/" + documentId + "/actions/cancel"))
                            .content(reverseCommand))
                    .andExpect(status().isForbidden());
            mvc.perform(full(post(ProductionInputHttpTest.ROOT + "/documents/" + documentId + "/actions/cancel"))
                            .content(reverseCommand))
                    .andExpect(status().isOk()).andExpect(jsonPath("document.status").value("REVERSED"));
            assertEquals(0, new BigDecimal("10000").compareTo(db.queryForObject(
                    "SELECT quantity FROM wm_inventory WHERE id=1", BigDecimal.class)));
            assertEquals(1, db.queryForObject("SELECT COUNT(*) FROM wm_stock_action WHERE action_type='RETURN'", Integer.class));
            assertEquals(1, db.queryForObject("SELECT COUNT(*) FROM wm_stock_action WHERE action_type='REVERSAL'", Integer.class));
            assertEquals(false, db.queryForObject("SELECT stock_applied FROM mes_material_issue_input WHERE document_id=?",
                    Boolean.class, documentId));
        }
    }

    @Test
    void startedExecutionWithoutEffectiveDownstreamFactsCanReverseItsIssue() throws Exception {
        try (var context = ProductionInputHttpTest.context()) {
            var mvc = MaterialIssueHttpTest.mvc(context);
            var db = ProductionInputHttpTest.db(context);
            var input = new ObjectMapper().readTree(ProductionInputHttpTest.submit(mvc,
                    ProductionInputHttpTest.command(mvc, UUID.randomUUID().toString(), "L001", "10")));
            String documentId = input.get("documentId").asText();
            String executionCode = input.get("executionCode").asText();

            mvc.perform(full(post("/api/production/executions/{id}/actions/start", executionCode)))
                    .andExpect(status().isOk()).andExpect(jsonPath("status").value("RUNNING"));
            var actionsBefore = db.queryForList("SELECT * FROM wm_stock_action ORDER BY id");
            var reverseCommand = StockDocumentHttpTest.cancellationBody(mvc, documentId);
            mvc.perform(full(post(ProductionInputHttpTest.ROOT + "/documents/" + documentId + "/actions/cancel"))
                            .content(reverseCommand))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("document.status").value("REVERSED"));

            assertEquals("REVERSED", db.queryForObject("SELECT status FROM wm_stock_document WHERE id=?",
                    String.class, documentId));
            assertEquals(false, db.queryForObject("SELECT stock_applied FROM mes_material_issue_input WHERE document_id=?",
                    Boolean.class, documentId));
            assertEquals(actionsBefore.size() + 1, db.queryForList("SELECT * FROM wm_stock_action ORDER BY id").size());
            assertEquals(1, db.queryForObject("SELECT COUNT(*) FROM wm_stock_action WHERE action_type='REVERSAL'", Integer.class));
            assertEquals(0, new BigDecimal("10000").compareTo(db.queryForObject(
                    "SELECT quantity FROM wm_inventory WHERE id=1", BigDecimal.class)));
            assertEquals("RUNNING", db.queryForObject("SELECT status FROM mes_production_execution WHERE execution_code=?",
                    String.class, executionCode));
            assertTrue(db.queryForObject("SELECT start_time IS NOT NULL FROM mes_production_execution WHERE execution_code=?",
                    Boolean.class, executionCode));
        }
    }

    @Test
    void startedExecutionStillKeepsTheExistingProductionReturnRestriction() throws Exception {
        try (var context = ProductionInputHttpTest.context()) {
            var mvc = MaterialIssueHttpTest.mvc(context);
            var db = ProductionInputHttpTest.db(context);
            var input = submitIssue(mvc, "L001", "10");
            String documentId = input.get("documentId").asText();
            String executionCode = input.get("executionCode").asText();
            var detail = new ObjectMapper().readTree(mvc.perform(full(get(StockDocumentHttpTest.OUT_ROOT + "/" + documentId)))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
            String lineId = detail.get("lines").get(0).get("id").asText();
            mvc.perform(full(post("/api/production/executions/{id}/actions/start", executionCode)))
                    .andExpect(status().isOk());
            String returnCommand = "{\"requestId\":\"" + UUID.randomUUID() + "\",\"stockId\":\"1\","
                    + "\"quantity\":\"2\",\"expectedQuantity\":\"9980\",\"expectedAvailable\":\"9980\","
                    + "\"reason\":\"TEST ONLY return after execution start\",\"documentId\":\"" + documentId
                    + "\",\"documentLineId\":\"" + lineId + "\"}";

            mvc.perform(full(post(ProductionInputHttpTest.ROOT + "/documents/" + documentId + "/returns"))
                            .content(returnCommand))
                    .andExpect(status().isConflict()).andExpect(jsonPath("errorCode").value("RESOURCE_CONFLICT"));

            assertEquals("POSTED", db.queryForObject("SELECT status FROM wm_stock_document WHERE id=?", String.class, documentId));
            assertEquals(true, db.queryForObject("SELECT stock_applied FROM mes_material_issue_input WHERE document_id=?", Boolean.class, documentId));
            assertEquals(0, db.queryForObject("SELECT COUNT(*) FROM wm_stock_action WHERE action_type='RETURN'", Integer.class));
            assertEquals(0, db.queryForObject("SELECT COUNT(*) FROM wm_stock_action WHERE action_type='REVERSAL'", Integer.class));
            assertEquals(0, new BigDecimal("9980").compareTo(db.queryForObject("SELECT quantity FROM wm_inventory WHERE id=1", BigDecimal.class)));
        }
    }

    @Test
    void anyEffectiveReportBlocksReversalEvenWhenRemainingInputWouldCoverIt() throws Exception {
        try (var context = ProductionInputHttpTest.context(ProductionReportingHttpTest.Extra.class)) {
            var mvc = MaterialIssueHttpTest.mvc(context);
            var db = ProductionInputHttpTest.db(context);
            db.update("INSERT INTO mes_operation_user(operation_id,user_id) VALUES(1,1)");
            var first = submitIssue(mvc, "L001", "100");
            var second = submitIssue(mvc, "L001", "50");
            String documentId = second.get("documentId").asText();
            String executionCode = second.get("executionCode").asText();
            submitReport(mvc, executionCode, 11, "80");
            var actionsBefore = db.queryForList("SELECT * FROM wm_stock_action ORDER BY id");
            var inventoryBefore = db.queryForObject("SELECT quantity FROM wm_inventory WHERE id=1", BigDecimal.class);

            mvc.perform(full(post(ProductionInputHttpTest.ROOT + "/documents/" + documentId + "/actions/cancel"))
                            .content(StockDocumentHttpTest.cancellationBody(mvc, documentId)))
                    .andExpect(status().isConflict()).andExpect(jsonPath("errorCode").value("RESOURCE_CONFLICT"));

            assertEquals("POSTED", db.queryForObject("SELECT status FROM wm_stock_document WHERE id=?", String.class, documentId));
            assertEquals(true, db.queryForObject("SELECT stock_applied FROM mes_material_issue_input WHERE document_id=?", Boolean.class, documentId));
            assertEquals(2, db.queryForObject("SELECT COUNT(*) FROM mes_material_issue_input WHERE execution_code=? AND stock_applied=TRUE", Integer.class, executionCode));
            assertEquals(0, new BigDecimal("100").compareTo(db.queryForObject(
                    "SELECT SUM(output_qty) FROM mes_material_issue_input WHERE execution_code=? AND document_id<>?", BigDecimal.class, executionCode, documentId)));
            assertEquals(actionsBefore, db.queryForList("SELECT * FROM wm_stock_action ORDER BY id"));
            assertEquals(0, inventoryBefore.compareTo(db.queryForObject("SELECT quantity FROM wm_inventory WHERE id=1", BigDecimal.class)));
            assertEquals(1, db.queryForObject("SELECT COUNT(*) FROM mes_production_report WHERE execution_code=? AND quantity_delta=80", Integer.class, executionCode));
            assertEquals(first.get("executionCode").asText(), executionCode);
        }
    }

    @Test
    void formallyVoidedReportNoLongerBlocksIssueReversal() throws Exception {
        try (var context = ProductionInputHttpTest.context(ProductionReportingHttpTest.Extra.class,
                ProductionDefectHttpTest.Extra.class)) {
            var mvc = MaterialIssueHttpTest.mvc(context);
            var db = ProductionInputHttpTest.db(context);
            when(context.getBean(BusinessDirectoryService.class).historicalUserExists(any())).thenReturn(true);
            db.update("INSERT INTO mes_operation_user(operation_id,user_id) VALUES(1,1)");
            var input = submitIssue(mvc, "L001", "10");
            String documentId = input.get("documentId").asText();
            String executionCode = input.get("executionCode").asText();
            var reportIds = new java.util.LinkedHashMap<Long, Long>();
            for (long node : List.of(11L, 12L, 13L))
                reportIds.put(node, submitReport(mvc, executionCode, node, "10"));
            String qualityId = ProductionDefectHttpTest.add(mvc, ProductionDefectHttpTest.command(
                    "CASE15-F-QUALITY", 0, "1").replace("\"executionCode\":\"1\"",
                    "\"executionCode\":\"" + executionCode + "\""), "A");
            assertTrue(businessQuery(context, () -> context.getBean(ProductionDefectListExtraMapper.class)
                    .hasEffectiveFacts(executionCode)));
            mvc.perform(ProductionReportHttpTest.full(post("/api/quality/defects/{id}/void", qualityId), "A")
                            .content("{\"requestKey\":\"CASE15-F-QUALITY-VOID\",\"expectedRevision\":1,\"revisionReason\":\"TEST ONLY void quality before input reversal\"}"))
                    .andExpect(status().isOk());
            assertEquals(false, businessQuery(context, () -> context.getBean(ProductionDefectListExtraMapper.class)
                    .hasEffectiveFacts(executionCode)));
            for (long node : List.of(13L, 12L, 11L)) {
                long reportId = reportIds.get(node);
                mvc.perform(full(post("/api/production/reports/{id}/void", reportId))
                                .content("{\"requestId\":\"" + UUID.randomUUID() + "\",\"reason\":\"TEST ONLY void report before input reversal\"}"))
                        .andExpect(status().isOk());
            }
            assertEquals(false, businessQuery(context, () -> context.getBean(
                    dev.mesbase.business.production.dao.ProductionQuantityMapper.class).hasEffectiveReports(executionCode)));

            mvc.perform(full(post(ProductionInputHttpTest.ROOT + "/documents/" + documentId + "/actions/cancel"))
                            .content(StockDocumentHttpTest.cancellationBody(mvc, documentId)))
                    .andExpect(status().isOk()).andExpect(jsonPath("document.status").value("REVERSED"));
            assertEquals(1, db.queryForObject("SELECT COUNT(*) FROM wm_stock_document_line l JOIN wm_stock_action a " +
                    "ON a.document_line_id=l.id WHERE l.document_id=? AND a.action_type='REVERSAL'", Integer.class, documentId));
            assertEquals("VOID", db.queryForObject("SELECT record_action FROM mes_production_defect " +
                    "WHERE declaration_id=? ORDER BY revision_no DESC LIMIT 1", String.class, qualityId));
            assertEquals(6, db.queryForObject("SELECT COUNT(*) FROM mes_production_report WHERE execution_code=?", Integer.class, executionCode));
        }
    }

    @Test
    void qualityFactAndFormalInboundQueriesRecognizeApiCreatedFacts() throws Exception {
        try (var context = ProductionInputHttpTest.context(ProductionReportingHttpTest.Extra.class,
                ProductionDefectHttpTest.Extra.class)) {
            var mvc = MaterialIssueHttpTest.mvc(context);
            var db = ProductionInputHttpTest.db(context);
            when(context.getBean(BusinessDirectoryService.class).historicalUserExists(any())).thenReturn(true);
            db.update("INSERT INTO mes_operation_user(operation_id,user_id) VALUES(1,1)");
            var input = submitIssue(mvc, "L001", "10");
            String documentId = input.get("documentId").asText();
            String executionCode = input.get("executionCode").asText();
            for (long node : List.of(11L, 12L, 13L)) submitReport(mvc, executionCode, node, "10");
            String qualityId = ProductionDefectHttpTest.add(mvc, ProductionDefectHttpTest.command(
                    "CASE15-D-QUALITY", 0, "1").replace("\"executionCode\":\"1\"",
                    "\"executionCode\":\"" + executionCode + "\""), "A");
            assertTrue(businessQuery(context, () -> context.getBean(ProductionDefectListExtraMapper.class)
                    .hasEffectiveFacts(executionCode)));
            mvc.perform(full(post(ProductionInputHttpTest.ROOT + "/documents/" + documentId + "/actions/cancel"))
                            .content(StockDocumentHttpTest.cancellationBody(mvc, documentId)))
                    .andExpect(status().isConflict()).andExpect(jsonPath("errorCode").value("RESOURCE_CONFLICT"));
            assertEquals("POSTED", db.queryForObject("SELECT status FROM wm_stock_document WHERE id=?", String.class, documentId));
            assertEquals("CREATE", db.queryForObject("SELECT record_action FROM mes_production_defect " +
                    "WHERE declaration_id=? ORDER BY revision_no DESC LIMIT 1", String.class, qualityId));
            assertEquals(0, db.queryForObject("SELECT COUNT(*) FROM wm_stock_action WHERE action_type='REVERSAL'", Integer.class));
        }
    }

    @Test
    void validFormalProductionInboundIsRecognizedAndBlocksIssueReversal() throws Exception {
        try (var context = ProductionInputHttpTest.context(ProductionReportingHttpTest.Extra.class)) {
            var mvc = MaterialIssueHttpTest.mvc(context);
            var db = ProductionInputHttpTest.db(context);
            db.update("INSERT INTO mes_operation_user(operation_id,user_id) VALUES(1,1)");
            var input = submitIssue(mvc, "L001", "10");
            String documentId = input.get("documentId").asText();
            String executionCode = input.get("executionCode").asText();
            for (long node : List.of(11L, 12L, 13L)) submitReport(mvc, executionCode, node, "10");
            db.update("INSERT INTO wm_inventory(id,material_id,warehouse_id,batch_no,quantity,available_quantity,status) " +
                    "SELECT 3,e.product_id,1,e.batch_no,0,0,'ENABLED' FROM mes_production_execution e WHERE e.execution_code=?",
                    executionCode);
            String inbound = "{\"requestId\":\"" + UUID.randomUUID() + "\",\"stockId\":3,\"quantity\":\"1\",\"reason\":\"TEST ONLY formal inbound before issue reversal\"}";
            mvc.perform(full(post("/api/production/executions/{id}/inbound", executionCode)).content(inbound))
                    .andExpect(status().isOk()).andExpect(jsonPath("document.status").value("POSTED"));
            assertEquals(0, new BigDecimal("1").compareTo(businessQuery(context, () -> context
                    .getBean(StockDocumentExtraMapper.class).formalProductionInbound(executionCode))));
            assertEquals(0L, businessQuery(context, () -> context.getBean(StockDocumentExtraMapper.class)
                    .invalidProductionInboundLines(executionCode)));

            mvc.perform(full(post(ProductionInputHttpTest.ROOT + "/documents/" + documentId + "/actions/cancel"))
                            .content(StockDocumentHttpTest.cancellationBody(mvc, documentId)))
                    .andExpect(status().isConflict()).andExpect(jsonPath("errorCode").value("RESOURCE_CONFLICT"));
            assertEquals("POSTED", db.queryForObject("SELECT status FROM wm_stock_document WHERE id=?", String.class, documentId));
            assertEquals(1, db.queryForObject("SELECT COUNT(*) FROM wm_stock_document WHERE source_execution_code=? " +
                    "AND document_type='PRODUCTION_RECEIPT' AND status='POSTED'", Integer.class, executionCode));
            assertEquals(0, db.queryForObject("SELECT COUNT(*) FROM wm_stock_action WHERE action_type='REVERSAL'", Integer.class));
        }
    }

    @Test
    void concurrentReportAndIssueReversalCannotBothCommit() throws Exception {
        try (var context = ProductionInputHttpTest.context(ProductionReportingHttpTest.Extra.class);
             var pool = Executors.newFixedThreadPool(2)) {
            var mvc = MaterialIssueHttpTest.mvc(context);
            var db = ProductionInputHttpTest.db(context);
            db.update("INSERT INTO mes_operation_user(operation_id,user_id) VALUES(1,1)");
            var input = submitIssue(mvc, "L001", "10");
            String documentId = input.get("documentId").asText();
            String executionCode = input.get("executionCode").asText();
            var barrier = new CyclicBarrier(2);
            var reversal = pool.submit(() -> {
                barrier.await();
                return mvc.perform(full(post(ProductionInputHttpTest.ROOT + "/documents/" + documentId + "/actions/cancel"))
                        .content(StockDocumentHttpTest.cancellationBody(mvc, documentId))).andReturn().getResponse().getStatus();
            });
            var report = pool.submit(() -> {
                barrier.await();
                return mvc.perform(full(post(ProductionReportingHttpTest.ROOT))
                        .content(ProductionReportingHttpTest.body(11, "5", UUID.randomUUID().toString())
                                .replace("\"executionCode\":\"1\"", "\"executionCode\":\"" + executionCode + "\"")))
                        .andReturn().getResponse().getStatus();
            });
            int reversalStatus = reversal.get();
            int reportStatus = report.get();
            assertTrue(reversalStatus == 200 && reportStatus == 409 || reversalStatus == 409 && reportStatus == 200,
                    "reversal=" + reversalStatus + ", report=" + reportStatus);
            int reportCount = db.queryForObject("SELECT COUNT(*) FROM mes_production_report WHERE execution_code=?",
                    Integer.class, executionCode);
            String documentStatus = db.queryForObject("SELECT status FROM wm_stock_document WHERE id=?",
                    String.class, documentId);
            if (reversalStatus == 200) {
                assertEquals(0, reportCount);
                assertEquals("REVERSED", documentStatus);
            } else {
                assertEquals(1, reportCount);
                assertEquals("POSTED", documentStatus);
            }
        }
    }

    private static tools.jackson.databind.JsonNode submitIssue(MockMvc mvc, String lot, String quantity) throws Exception {
        String body = ProductionInputHttpTest.command(mvc, UUID.randomUUID().toString(), lot, quantity);
        return new ObjectMapper().readTree(ProductionInputHttpTest.submit(mvc, body));
    }

    private static long submitReport(MockMvc mvc, String executionCode, long node, String quantity) throws Exception {
        String body = ProductionReportingHttpTest.body(node, quantity, UUID.randomUUID().toString())
                .replace("\"executionCode\":\"1\"", "\"executionCode\":\"" + executionCode + "\"");
        var response = mvc.perform(full(post(ProductionReportingHttpTest.ROOT)).content(body))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return new ObjectMapper().readTree(response).get("id").asLong();
    }

    private static <T> T businessQuery(AnnotationConfigWebApplicationContext context,
                                       java.util.function.Supplier<T> query) {
        var transaction = new TransactionTemplate(context.getBean("businessTransactionManager",
                PlatformTransactionManager.class));
        var result = new AtomicReference<T>();
        FactoryContext.runWithScope(new FactoryScope(new FactoryId("A")), () ->
                result.set(transaction.execute(status -> query.get())));
        return result.get();
    }

    @Test
    void localApprovalAndCancellationUseRealStockFactsWithoutWarehouseCommands() throws Exception {
        try (var context = ProductionInputHttpTest.context()) {
            var mvc = MaterialIssueHttpTest.mvc(context);
            var db = ProductionInputHttpTest.db(context);
            String command = ProductionInputHttpTest.command(mvc, UUID.randomUUID().toString(), "L001", "10");
            var result = new ObjectMapper().readTree(ProductionInputHttpTest.submit(mvc, command));
            String issue = result.get("documentId").asText();
            String document = result.get("documentId").asText();
            assertEquals("POSTED", db.queryForObject("SELECT status FROM wm_stock_document WHERE id=?", String.class, issue));
            assertEquals(0, new BigDecimal("9980").compareTo(db.queryForObject("SELECT quantity FROM wm_inventory WHERE id=1", BigDecimal.class)));
            String requestBody = StockDocumentHttpTest.cancellationBody(mvc, document);
            mvc.perform(full(post(ProductionInputHttpTest.ROOT + "/documents/" + document + "/actions/cancel"))
                            .content(requestBody))
                    .andExpect(status().isOk());
            String operation = db.queryForObject("SELECT r.id FROM wm_stock_document_line l JOIN wm_stock_action r ON r.reference_action_id=l.stock_action_id WHERE l.document_id=? AND r.action_type='REVERSAL'", String.class, issue);
            mvc.perform(full(post(ProductionInputHttpTest.ROOT + "/documents/" + document + "/actions/cancel"))
                            .content(requestBody))
                    .andExpect(status().isOk());
            assertEquals("REVERSED", db.queryForObject("SELECT status FROM wm_stock_document WHERE id=?", String.class, issue));
            assertEquals(operation, db.queryForObject("SELECT r.id FROM wm_stock_document_line l JOIN wm_stock_action r ON r.reference_action_id=l.stock_action_id WHERE l.document_id=? AND r.action_type='REVERSAL'", String.class, issue));
            assertEquals(false, db.queryForObject("SELECT stock_applied FROM mes_material_issue_input WHERE document_id=?", Boolean.class, issue));
            assertEquals(0, new BigDecimal("10000").compareTo(db.queryForObject("SELECT quantity FROM wm_inventory WHERE id=1", BigDecimal.class)));
            assertEquals(2, db.queryForObject("SELECT COUNT(*) FROM wm_stock_action", Integer.class));
        }
    }

    @Test
    void reversalFailureRollsBackCancellationIdentityInputAndStockTogether() throws Exception {
        try (var context = ProductionInputHttpTest.context()) {
            var mvc = MaterialIssueHttpTest.mvc(context);
            var db = ProductionInputHttpTest.db(context);
            var result = new ObjectMapper().readTree(ProductionInputHttpTest.submit(mvc,
                    ProductionInputHttpTest.command(mvc, UUID.randomUUID().toString(), "L001", "10")));
            var tables = List.of("wm_stock_document", "mes_material_issue_input", "wm_inventory", "wm_stock_action");
            var before = tables.stream().map(table -> db.queryForList("SELECT * FROM " + table + " ORDER BY id"))
                    .toList();
            db.execute("ALTER TABLE wm_stock_action ADD CONSTRAINT test_fail_reversal CHECK(action_type<>'REVERSAL')");
            String document = result.get("documentId").asText();
            mvc.perform(full(post(ProductionInputHttpTest.ROOT + "/documents/" + document + "/actions/cancel"))
                            .content(StockDocumentHttpTest.cancellationBody(mvc, document)))
                    .andExpect(status().isInternalServerError());
            assertEquals(before, tables.stream()
                    .map(table -> db.queryForList("SELECT * FROM " + table + " ORDER BY id")).toList());
        }
    }

    @Test
    void aDifferentCancellationRequestCannotRepeatAnExistingReversal() throws Exception {
        try (var context = ProductionInputHttpTest.context()) {
            var mvc = MaterialIssueHttpTest.mvc(context);
            var db = ProductionInputHttpTest.db(context);
            var result = new ObjectMapper().readTree(ProductionInputHttpTest.submit(mvc,
                    ProductionInputHttpTest.command(mvc, UUID.randomUUID().toString(), "L001", "10")));
            String document = result.get("documentId").asText();
            String firstRequest = StockDocumentHttpTest.cancellationBody(mvc, document);
            mvc.perform(full(post(ProductionInputHttpTest.ROOT + "/documents/" + document + "/actions/cancel"))
                            .content(firstRequest)).andExpect(status().isOk());
            var tables = List.of("wm_stock_document", "mes_material_issue_input", "wm_inventory", "wm_stock_action");
            var before = tables.stream().map(table -> db.queryForList("SELECT * FROM " + table + " ORDER BY id"))
                    .toList();
            mvc.perform(full(post(ProductionInputHttpTest.ROOT + "/documents/" + document + "/actions/cancel"))
                            .content(StockDocumentHttpTest.cancellationBody(mvc, document)))
                    .andExpect(status().isConflict());
            assertEquals(before, tables.stream()
                    .map(table -> db.queryForList("SELECT * FROM " + table + " ORDER BY id")).toList());
        }
    }

    @Test
    void missingStockEffectDoesNotPretendAnInputWasSuccessfullyReversed() throws Exception {
        try (var context = ProductionInputHttpTest.context()) {
            var mvc = MaterialIssueHttpTest.mvc(context);
            var db = ProductionInputHttpTest.db(context);
            var result = new ObjectMapper().readTree(ProductionInputHttpTest.submit(mvc,
                    ProductionInputHttpTest.command(mvc, UUID.randomUUID().toString(), "L001", "10")));
            String document = result.get("documentId").asText();
            db.update("UPDATE mes_material_issue_input SET stock_applied=FALSE WHERE document_id=?", document);
            mvc.perform(full(post(ProductionInputHttpTest.ROOT + "/documents/" + document + "/actions/cancel"))
                            .content(StockDocumentHttpTest.cancellationBody(mvc, document)))
                    .andExpect(status().isConflict());
            assertEquals(0, db.queryForObject("SELECT COUNT(*) FROM wm_stock_action WHERE action_type='REVERSAL'", Integer.class));
            assertEquals(0, new BigDecimal("9980").compareTo(db.queryForObject("SELECT quantity FROM wm_inventory WHERE id=1", BigDecimal.class)));
        }
    }
}
