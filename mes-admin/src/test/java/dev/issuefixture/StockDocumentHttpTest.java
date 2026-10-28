package dev.issuefixture;

import dev.mesbase.acceptance.FactorySnapshotFixture;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.List;
import java.util.concurrent.Executors;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static dev.issuefixture.ProductionCoreV1HttpTest.full;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * TEST ONLY H2: real generated SQL and Spring document/stock transactions; not a live MySQL receipt.
 */
class StockDocumentHttpTest {
    static final String ROOT = "/api/wm/stock-documents";
    static final ObjectMapper JSON = new ObjectMapper();

    static JsonNode create(MockMvc m, String type, String order) throws Exception {
        String ruleCode = List.of("PURCHASE_RECEIPT", "PRODUCTION_RECEIPT", "OTHER_RECEIPT", "DEFECT_RECEIPT", "SCRAP_RECEIPT")
                .contains(type) ? "TEST_STOCK_IN" : "TEST_STOCK_OUT";
        String body = "{\"documentType\":\"" + type + "\",\"reason\":\"TEST ONLY stock document\",\"ruleCode\":\""
                + ruleCode + "\""
                + (order == null ? "" : ",\"sourceWorkorderId\":\"" + order + "\"") + "}";
        return JSON.readTree(m.perform(full(post(ROOT)).content(body)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
    }

    static String line(String revision, String stock, String batch, String quantity) {
        return "{\"expectedRevision\":\"" + revision + "\",\"stockId\":\"" + stock
                + "\",\"materialId\":\"1\",\"warehouseId\":\"1\",\"unitId\":\"1\",\"batchNo\":\""
                + batch + "\",\"binCode\":\"BIN1\",\"quantity\":\"" + quantity
                + "\",\"remark\":\"TEST ONLY physically counted against original handover\"}";
    }

    static void add(MockMvc m, String id, String revision, String stock, String batch, String qty) throws Exception {
        m.perform(full(post(ROOT + "/" + id + "/lines")).content(line(revision, stock, batch, qty)))
                .andExpect(status().isOk());
    }

    static String intent(String uuid, int revision) {
        return "{\"requestId\":\"" + uuid + "\",\"expectedRevision\":\"" + revision + "\"}";
    }

    static void balance(JdbcTemplate j, String value) {
        assertEquals(0, new BigDecimal(value).compareTo(j.queryForObject("SELECT quantity FROM wm_inventory WHERE id=1", BigDecimal.class)));
    }

    @Test
    void aggregateSaveCreatesHeaderAndItemsAtomically() throws Exception {
        try (var c = ProductionInputHttpTest.context()) {
            var mvc = MaterialIssueHttpTest.mvc(c);
            var db = ProductionInputHttpTest.db(c);
            String valid = "{\"header\":{\"documentType\":\"OTHER_RECEIPT\",\"reason\":\"aggregate receipt\",\"ruleCode\":\"TEST_STOCK_IN\"},"
                    + "\"items\":[{\"stockId\":\"1\",\"materialId\":\"1\",\"warehouseId\":\"1\",\"unitId\":\"1\",\"batchNo\":\"L001\",\"binCode\":\"BIN1\",\"quantity\":\"1\"},"
                    + "{\"stockId\":\"1\",\"materialId\":\"1\",\"warehouseId\":\"1\",\"unitId\":\"1\",\"batchNo\":\"L001\",\"binCode\":\"BIN1\",\"quantity\":\"2\"}]}";
            var created = JSON.readTree(mvc.perform(full(post(ROOT + "/aggregate")).content(valid))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
            assertEquals(2, created.get("lines").size());
            assertEquals(1, db.queryForObject("SELECT COUNT(*) FROM wm_stock_document", Integer.class));
            assertEquals(2, db.queryForObject("SELECT COUNT(*) FROM wm_stock_document_line", Integer.class));

            String invalid = "{\"header\":{\"documentType\":\"OTHER_RECEIPT\",\"reason\":\"must rollback\",\"ruleCode\":\"TEST_STOCK_IN\"},"
                    + "\"items\":[{\"stockId\":\"1\",\"materialId\":\"1\",\"warehouseId\":\"1\",\"unitId\":\"1\",\"batchNo\":\"L001\",\"binCode\":\"BIN1\",\"quantity\":\"1\"},"
                    + "{\"stockId\":\"1\",\"materialId\":\"999\",\"warehouseId\":\"1\",\"unitId\":\"1\",\"batchNo\":\"L001\",\"binCode\":\"BIN1\",\"quantity\":\"1\"}]}";
            mvc.perform(full(post(ROOT + "/aggregate")).content(invalid)).andExpect(status().isConflict());
            assertEquals(1, db.queryForObject("SELECT COUNT(*) FROM wm_stock_document", Integer.class));
            assertEquals(2, db.queryForObject("SELECT COUNT(*) FROM wm_stock_document_line", Integer.class));
        }
    }

    @Test
    void receiptIssueReplayAndWholeDocumentRollback() throws Exception {
        try (var c = ProductionInputHttpTest.context()) {
            var m = MaterialIssueHttpTest.mvc(c);
            var j = ProductionInputHttpTest.db(c);
            j.update("UPDATE wm_inventory SET quantity=0,available_quantity=0");
            String receipt = create(m, "OTHER_RECEIPT", null).get("document").get("id").asText();
            add(m, receipt, "0", "1", "L001", "100");
            balance(j, "0");
            String receive = intent(UUID.randomUUID().toString(), 1);
            m.perform(full(post(ROOT + "/" + receipt + "/actions/approve")).content(receive))
                    .andExpect(status().isOk());
            balance(j, "100");
            m.perform(full(post(ROOT + "/" + receipt + "/actions/approve")).content(receive))
                    .andExpect(status().isOk());
            m.perform(full(post(ROOT + "/" + receipt + "/actions/approve")).content(receive.replace("\"1\"", "\"2\"")))
                    .andExpect(status().isConflict());
            m.perform(full(post(ROOT + "/" + receipt + "/actions/approve")).content(intent(UUID.randomUUID()
                    .toString(), 1))).andExpect(status().isConflict());
            m.perform(full(put(ROOT + "/" + receipt)).content("{\"reason\":\"changed\",\"expectedRevision\":2}"))
                    .andExpect(status().isConflict());
            m.perform(full(delete(ROOT + "/" + receipt))).andExpect(status().isMethodNotAllowed());
            String issue = create(m, "OTHER_ISSUE", null).get("document").get("id").asText();
            add(m, issue, "0", "1", "L001", "30");
            String out = intent(UUID.randomUUID().toString(), 1);
            m.perform(full(post(ROOT + "/" + issue + "/actions/approve")).content(out)).andExpect(status().isOk());
            m.perform(full(post(ROOT + "/" + issue + "/actions/approve")).content(out)).andExpect(status().isOk());
            balance(j, "70");
            assertEquals(2, j.queryForObject("SELECT COUNT(*) FROM wm_stock_action WHERE operation_id IS NOT NULL AND document_line_id IS NOT NULL", Integer.class));
            String atomic = create(m, "OTHER_ISSUE", null).get("document").get("id").asText();
            add(m, atomic, "0", "1", "L001", "10");
            add(m, atomic, "1", "2", "L002", "1");
            m.perform(full(post(ROOT + "/" + atomic + "/actions/approve")).content(intent(UUID.randomUUID()
                    .toString(), 2))).andExpect(status().isConflict());
            balance(j, "70");
            assertEquals("DRAFT", j.queryForObject("SELECT status FROM wm_stock_document WHERE id=?", String.class, atomic));
            assertEquals(2, j.queryForObject("SELECT COUNT(*) FROM wm_stock_action", Integer.class));
            FactorySnapshotFixture.set(c, FactorySnapshotFixture.erp("A", "TEST", "TEST"));
            m.perform(full(post(ROOT + "/" + receipt + "/actions/approve")).content(receive))
                    .andExpect(status().isOk());
            m.perform(full(post(ROOT)).content("{\"documentType\":\"OTHER_RECEIPT\",\"reason\":\"blocked ERP\",\"ruleCode\":\"TEST_STOCK_IN\"}"))
                    .andExpect(status().isConflict());
        }
    }

    @Test
    void versionsCancelValidationScopeAndPermissions() throws Exception {
        try (var c = ProductionInputHttpTest.context()) {
            var m = MaterialIssueHttpTest.mvc(c);
            String id = create(m, "OTHER_RECEIPT", null).get("document").get("id").asText();
            m.perform(post(ROOT + "/" + id + "/actions/approve").header("Authorization", "Bearer CRUD")
                    .header("X-Factory-Id", "A").contentType("application/json")
                    .content(intent(UUID.randomUUID().toString(), 0))).andExpect(status().isForbidden());
            m.perform(full(post(ROOT + "/" + id + "/actions/approve")).content(intent(UUID.randomUUID().toString(), 0)))
                    .andExpect(status().isConflict());
            m.perform(full(post(ROOT + "/" + id + "/lines")).content(line("0", "999", "L001", "1")))
                    .andExpect(status().isConflict());
            m.perform(full(post(ROOT + "/" + id + "/lines")).content(line("0", "1", "L001", "1").replace("\"unitId\":\"1\"", "\"unitId\":\"2\"")))
                    .andExpect(status().isConflict());
            add(m, id, "0", "1", "L001", "1");
            m.perform(full(put(ROOT + "/" + id)).content("{\"expectedRevision\":0,\"reason\":\"stale\"}"))
                    .andExpect(status().isConflict());
            m.perform(full(post(ROOT + "/" + id + "/actions/cancel")).content("{\"expectedRevision\":1}"))
                    .andExpect(status().isOk());
            m.perform(full(post(ROOT + "/" + id + "/actions/approve")).content(intent(UUID.randomUUID().toString(), 2)))
                    .andExpect(status().isConflict());
            m.perform(get(ROOT + "/" + id).header("Authorization", "Bearer FULL").header("X-Factory-Id", "B"))
                    .andExpect(status().isNotFound());
        }
    }

    @Test
    void racingPostsAndRepeatedStockLinesNeverDoubleSpend() throws Exception {
        try (var c = ProductionInputHttpTest.context(); var pool = Executors.newFixedThreadPool(2)) {
            var m = MaterialIssueHttpTest.mvc(c);
            var j = ProductionInputHttpTest.db(c);
            j.update("UPDATE wm_inventory SET quantity=70,available_quantity=70 WHERE id=1");
            String id = create(m, "OTHER_ISSUE", null).get("document").get("id").asText();
            add(m, id, "0", "1", "L001", "30");
            add(m, id, "1", "1", "L001", "30");
            var a = pool.submit(() -> m.perform(full(post(ROOT + "/" + id + "/actions/approve")).content(intent(UUID.randomUUID()
                    .toString(), 2))).andReturn().getResponse().getStatus());
            var b = pool.submit(() -> m.perform(full(post(ROOT + "/" + id + "/actions/approve")).content(intent(UUID.randomUUID()
                    .toString(), 2))).andReturn().getResponse().getStatus());
            var results = List.of(a.get(), b.get());
            assertEquals(1, results.stream().filter(code -> code == 200).count());
            assertEquals(1, results.stream().filter(code -> code == 409).count());
            balance(j, "10");
            assertEquals(2, j.queryForObject("SELECT COUNT(*) FROM wm_stock_action", Integer.class));
        }
    }

    @Test
    void cancelledOrderUsesOnlyUnreceivedHandoverAndPreservesProductionFacts() throws Exception {
        try (var c = ProductionWarehouseFixture.context()) {
            var m = MaterialIssueHttpTest.mvc(c);
            var j = ProductionWarehouseFixture.db(c);
            String receipt = ProductionWarehouseFixture.legacy(m, j);
            ProductionWarehouseFixture.legacyAccepted(j, receipt, "60");
            String version = j.queryForObject("SELECT revision FROM workorder WHERE id=1", String.class);
            m.perform(full(post("/api/production/workorder/1/actions/cancel"))
                            .content("{\"requestId\":\"" + UUID.randomUUID() + "\",\"expectedRevision\":" + version + ",\"reason\":\"TEST ONLY warehouse handover remainder\"}"))
                    .andExpect(status().isOk());
            var reports = j.queryForList("SELECT * FROM mes_production_report ORDER BY id");
            var quality = j.queryForList("SELECT * FROM mes_quality_release ORDER BY id");
            var handover = j.queryForList("SELECT * FROM mes_production_receipt ORDER BY id");
            var accepted = j.queryForList("SELECT * FROM mes_warehouse_acceptance ORDER BY id");
            var execution = j.queryForList("SELECT * FROM mes_production_execution ORDER BY id");
            String id = create(m, "OTHER_RECEIPT", "1").get("document").get("id").asText();
            m.perform(full(post(ROOT + "/" + id + "/lines"))
                            .content(line("0", "1", "TEST-FINISHED", "20").replace("\"binCode\":\"BIN1\"", "\"binCode\":null")))
                    .andExpect(status().isOk());
            m.perform(full(post(ROOT + "/" + id + "/actions/approve"))
                    .content(intent(UUID.randomUUID().toString(), 1))).andExpect(status().isOk());
            balance(j, "80");
            String second = create(m, "OTHER_RECEIPT", "1").get("document").get("id").asText();
            m.perform(full(post(ROOT + "/" + second + "/lines"))
                            .content(line("0", "1", "TEST-FINISHED", "1").replace("\"binCode\":\"BIN1\"", "\"binCode\":null")))
                    .andExpect(status().isOk());
            m.perform(full(post(ROOT + "/" + second + "/actions/approve"))
                    .content(intent(UUID.randomUUID().toString(), 1))).andExpect(status().isConflict());
            assertEquals(reports, j.queryForList("SELECT * FROM mes_production_report ORDER BY id"));
            assertEquals(quality, j.queryForList("SELECT * FROM mes_quality_release ORDER BY id"));
            assertEquals(handover, j.queryForList("SELECT * FROM mes_production_receipt ORDER BY id"));
            assertEquals(accepted, j.queryForList("SELECT * FROM mes_warehouse_acceptance ORDER BY id"));
            assertEquals(execution, j.queryForList("SELECT * FROM mes_production_execution ORDER BY id"));
        }
    }

    @Test
    void crossDocumentUuidAndInjectedLineFailureRollbackAllStockFacts() throws Exception {
        try (var c = ProductionInputHttpTest.context()) {
            var m = MaterialIssueHttpTest.mvc(c);
            var j = ProductionInputHttpTest.db(c);
            j.update("UPDATE wm_inventory SET quantity=0,available_quantity=0");
            String first = create(m, "OTHER_RECEIPT", null).get("document").get("id").asText();
            add(m, first, "0", "1", "L001", "10");
            String uuid = UUID.randomUUID().toString();
            m.perform(full(post(ROOT + "/" + first + "/actions/approve")).content(intent(uuid, 1)))
                    .andExpect(status().isOk());
            String duplicate = create(m, "OTHER_RECEIPT", null).get("document").get("id").asText();
            add(m, duplicate, "0", "1", "L001", "10");
            m.perform(full(post(ROOT + "/" + duplicate + "/actions/approve")).content(intent(uuid, 1)))
                    .andExpect(status().isConflict());
            balance(j, "10");
            String failing = create(m, "OTHER_RECEIPT", null).get("document").get("id").asText();
            add(m, failing, "0", "1", "L001", "10");
            add(m, failing, "1", "2", "L002", "10");
            j.execute("ALTER TABLE wm_stock_document_line ADD CONSTRAINT test_document_failure CHECK(stock_id<>2 OR stock_action_id IS NULL)");
            m.perform(full(post(ROOT + "/" + failing + "/actions/approve")).content(intent(UUID.randomUUID()
                    .toString(), 2))).andExpect(status().is5xxServerError());
            balance(j, "10");
            assertEquals(1, j.queryForObject("SELECT COUNT(*) FROM wm_stock_action", Integer.class));
            assertEquals(0, j.queryForObject("SELECT COUNT(*) FROM wm_stock_document_line WHERE document_id=? AND stock_action_id IS NOT NULL", Integer.class, failing));
        }
    }

    @Test
    void documentAndFormalProductionIssueCompeteOnTheSameOwner() throws Exception {
        try (var c = ProductionInputHttpTest.context(); var pool = Executors.newFixedThreadPool(2)) {
            var m = MaterialIssueHttpTest.mvc(c);
            var j = ProductionInputHttpTest.db(c);
            j.update("UPDATE wm_inventory SET quantity=70,available_quantity=70 WHERE id=1");
            String id = create(m, "OTHER_ISSUE", null).get("document").get("id").asText();
            add(m, id, "0", "1", "L001", "50");
            String production = ProductionInputHttpTest.command(m, UUID.randomUUID().toString(), "L001", "20");
            var a = pool.submit(() -> m.perform(full(post(ROOT + "/" + id + "/actions/approve"))
                    .content(intent(UUID.randomUUID().toString(), 1))).andReturn().getResponse().getStatus());
            var b = pool.submit(() -> m.perform(full(post("/api/production/input"))
                    .content(production)).andReturn().getResponse());
            int documentStatus = a.get();
            var input = b.get();
            int inputStatus = input.getStatus();
            assertEquals(1, List.of(documentStatus, inputStatus).stream().filter(code -> code == 200).count());
            if (documentStatus == 200) {
                // The current supply calculation may observe the committed debit before taking its write lock.
                assertTrue(inputStatus == 400 || inputStatus == 409);
                String error = JSON.readTree(input.getContentAsString()).get("errorCode").asText();
                if (inputStatus == 400) assertEquals("BAD_REQUEST", error);
                else assertTrue(List.of("PRODUCTION_INPUT_STALE", "RESOURCE_CONFLICT").contains(error), error);
                assertEquals(0, new BigDecimal("20").compareTo(j.queryForObject(
                        "SELECT quantity FROM wm_inventory WHERE id=1", BigDecimal.class)));
                assertEquals(0, j.queryForObject("SELECT COUNT(*) FROM wm_stock_document WHERE production_confirmed_at IS NOT NULL", Integer.class));
            } else {
                assertEquals(409, documentStatus);
                assertEquals(200, inputStatus);
                assertEquals(0, new BigDecimal("30").compareTo(j.queryForObject(
                        "SELECT quantity FROM wm_inventory WHERE id=1", BigDecimal.class)));
                assertEquals(1, j.queryForObject("SELECT COUNT(*) FROM wm_stock_document WHERE production_confirmed_at IS NOT NULL", Integer.class));
            }
            assertEquals(1, j.queryForObject("SELECT COUNT(*) FROM wm_stock_action", Integer.class));
        }
    }

    @Test
    void cancelledWithoutProofAndExternalWarehouseFailClosedAndStockBinsStaySeparate() throws Exception {
        try (var c = ProductionInputHttpTest.context()) {
            var m = MaterialIssueHttpTest.mvc(c);
            var j = ProductionInputHttpTest.db(c);
            m.perform(full(post(ROOT)).content("{\"documentType\":\"OTHER_RECEIPT\",\"reason\":\"TEST ONLY\",\"sourceWorkorderId\":1,\"ruleCode\":\"TEST_STOCK_IN\"}"))
                    .andExpect(status().isConflict());
            j.update("UPDATE workorder SET status='CANCELLED' WHERE id=1");
            String cancelled = create(m, "OTHER_RECEIPT", "1").get("document").get("id").asText();
            add(m, cancelled, "0", "1", "L001", "1");
            m.perform(full(post(ROOT + "/" + cancelled + "/actions/approve"))
                    .content(intent(UUID.randomUUID().toString(), 1))).andExpect(status().isConflict());
            String regular = create(m, "OTHER_RECEIPT", null).get("document").get("id").asText();
            add(m, regular, "0", "1", "L001", "10");
            j.update("UPDATE wm_warehouse SET source='EXTERNAL' WHERE id=1");
            m.perform(full(post(ROOT + "/" + regular + "/actions/approve"))
                    .content(intent(UUID.randomUUID().toString(), 1))).andExpect(status().isConflict());
            j.update("UPDATE wm_warehouse SET source='LOCAL' WHERE id=1");
            j.update("INSERT INTO wm_inventory(id,material_id,warehouse_id,quantity,available_quantity,status,batch_no,bin_code) VALUES(3,1,1,7,7,'ENABLED','L001','BIN2')");
            m.perform(full(post(ROOT + "/" + regular + "/actions/approve"))
                    .content(intent(UUID.randomUUID().toString(), 1))).andExpect(status().isOk());
            assertEquals(0, new BigDecimal("7").compareTo(j.queryForObject("SELECT quantity FROM wm_inventory WHERE id=3", BigDecimal.class)));
            assertEquals(0, new BigDecimal("10000").compareTo(j.queryForObject("SELECT quantity FROM wm_inventory WHERE id=2", BigDecimal.class)));
        }
    }

    @Test
    void unavailableSourcesStayDraftAndCannotApproveOrChangeStock() throws Exception {
        try (var c = ProductionInputHttpTest.context()) {
            var m = MaterialIssueHttpTest.mvc(c);
            var j = ProductionInputHttpTest.db(c);
            j.update("UPDATE wm_inventory SET quantity=0,available_quantity=0");
            var catalog = JSON.readTree(m.perform(full(get(ROOT + "/types"))).andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString());
            assertEquals(11, catalog.size());
            for (String type : List.of("PURCHASE_RECEIPT", "OUTSOURCE_RECEIPT", "MATERIAL_RETURN_RECEIPT", "REWORK_RECEIPT", "SALES_RETURN_RECEIPT")) {
                var draft = create(m, type, null).get("document");
                String id = draft.get("id").asText();
                assertEquals("DRAFT", draft.get("status").asText());
                add(m, id, "0", "1", "L001", "5");
                m.perform(full(post(ROOT + "/" + id + "/actions/approve"))
                                .content(intent(UUID.randomUUID().toString(), 1)))
                        .andExpect(status().isUnprocessableEntity());
                assertEquals("DRAFT", j.queryForObject("SELECT status FROM wm_stock_document WHERE id=?", String.class, id));
                balance(j, "0");
            }
            for (String type : List.of("DEFECT_RECEIPT", "SCRAP_RECEIPT"))
                m.perform(full(post(ROOT)).content("{\"documentType\":\"" + type + "\",\"reason\":\"no quality source\",\"ruleCode\":\"TEST_STOCK_IN\"}"))
                        .andExpect(status().isUnprocessableEntity());
            assertEquals(0, j.queryForObject("SELECT COUNT(*) FROM wm_stock_action", Integer.class));
        }
    }


    @Test
    void tighteningOldDraftDoesNotRewriteFactsAndPostingRechecksCurrentUnit() throws Exception {
        try (var c = ProductionInputHttpTest.context()) {
            var m = MaterialIssueHttpTest.mvc(c);
            var j = ProductionInputHttpTest.db(c);
            j.update("UPDATE wm_inventory SET quantity=0,available_quantity=0");
            String id = create(m, "OTHER_RECEIPT", null).get("document").get("id").asText();
            add(m, id, "0", "1", "L001", "1.234567");
            var stored = JSON.readTree(m.perform(full(get(ROOT + "/" + id))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
            String lineId = stored.get("lines").get(0).get("id").asText();
            j.update("UPDATE unit_measure SET must_be_whole_number=1,quantity_precision=0 WHERE id=1");
            m.perform(full(put(ROOT + "/" + id + "/lines/" + lineId)).content(line("1", "1", "L001", "1.234567"))).andExpect(status().isOk());
            m.perform(full(post(ROOT + "/" + id + "/actions/approve")).content(intent(UUID.randomUUID().toString(), 2))).andExpect(status().isBadRequest());
            balance(j, "0");
            assertEquals(0, j.queryForObject("SELECT COUNT(*) FROM wm_stock_action", Integer.class));
            assertEquals(new BigDecimal("1.234567"), j.queryForObject("SELECT quantity FROM wm_stock_document_line WHERE id=?", BigDecimal.class, lineId));
            m.perform(full(put(ROOT + "/" + id + "/lines/" + lineId)).content(line("2", "1", "L001", "1"))).andExpect(status().isOk());
            String request = UUID.randomUUID().toString();
            m.perform(full(post(ROOT + "/" + id + "/actions/approve")).content(intent(request, 3))).andExpect(status().isOk());
            m.perform(full(post(ROOT + "/" + id + "/actions/approve")).content(intent(request, 3))).andExpect(status().isOk());
            balance(j, "1");
            assertEquals(1, j.queryForObject("SELECT COUNT(*) FROM wm_stock_action", Integer.class));
        }
    }

    @Test
    void sameIdsInTwoFactoriesUseTheirOwnPersistedUnitsAndWriteOnlyTheirOwnLedger() throws Exception {
        try (var c = ProductionInputHttpTest.context()) {
            var m = MaterialIssueHttpTest.mvc(c);
            var db = c.getBean(MaterialIssueHttpTest.Fixture.class).business;
            db.get("A").update("UPDATE unit_measure SET must_be_whole_number=0,quantity_precision=6 WHERE id=1");
            db.get("B").update("UPDATE unit_measure SET must_be_whole_number=1,quantity_precision=0 WHERE id=1");
            String a = create(m, "OTHER_RECEIPT", null).get("document").get("id").asText();
            String b = JSON.readTree(m.perform(ProductionReportHttpTest.full(post(ROOT), "B").content("{\"documentType\":\"OTHER_RECEIPT\",\"reason\":\"isolated factory\",\"ruleCode\":\"TEST_STOCK_IN\"}")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).get("document").get("id").asText();
            assertEquals(a, b);
            add(m, a, "0", "1", "L001", "1.234567");
            m.perform(ProductionReportHttpTest.full(post(ROOT + "/" + b + "/lines"), "B").content(line("0", "1", "L001", "1.234567"))).andExpect(status().isBadRequest());
            m.perform(full(post(ROOT + "/" + a + "/actions/approve")).content(intent(UUID.randomUUID().toString(), 1))).andExpect(status().isOk());
            assertEquals(1, db.get("A").queryForObject("SELECT COUNT(*) FROM wm_stock_action", Integer.class));
            assertEquals(0, db.get("B").queryForObject("SELECT COUNT(*) FROM wm_stock_action", Integer.class));
            assertEquals(0, new BigDecimal("10001.234567").compareTo(db.get("A").queryForObject("SELECT quantity FROM wm_inventory WHERE id=1", BigDecimal.class)));
            assertEquals(0, new BigDecimal("10000").compareTo(db.get("B").queryForObject("SELECT quantity FROM wm_inventory WHERE id=1", BigDecimal.class)));
        }
    }
}
