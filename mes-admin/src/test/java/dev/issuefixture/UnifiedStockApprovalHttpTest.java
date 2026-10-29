package dev.issuefixture;

import dev.mesbase.core.security.AuthorizationService;
import java.math.BigDecimal;
import java.util.Map;
import java.util.List;
import java.util.stream.Stream;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import static dev.issuefixture.ProductionCoreV1HttpTest.full;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Independent isolated H2 HTTP/SQL transaction regression; never a deployed MySQL acceptance.
 */
class UnifiedStockApprovalHttpTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String GENERIC_AGGREGATE = "{\"header\":{\"direction\":\"IN\",\"documentType\":\"OTHER_RECEIPT\",\"reason\":\"TEST ONLY permission\",\"ruleCode\":\"OTHER_RECEIPT\"},\"items\":[{\"stockId\":\"1\",\"materialId\":\"1\",\"warehouseId\":\"1\",\"unitId\":\"1\",\"batchNo\":\"L001\",\"binCode\":\"BIN1\",\"quantity\":\"1\"}]}";

    @Configuration
    static class AuditWithoutDraftEditing {
        @Bean
        AtomicBoolean auditOnly() {
            return new AtomicBoolean();
        }

        @Bean
        @Primary
        AuthorizationService warehouseAuditOnly(AtomicBoolean auditOnly) {
            return (actor, scope, permission) -> !auditOnly.get()
                    || !List.of("business:wm:stock:document:create", "business:wm:stock:document:update")
                    .contains(permission.code());
        }
    }

    @Test
    void productionPermissionApprovesIssuesWithoutGrantingGenericDocumentCreation() throws Exception {
        try (var context = ProductionInputHttpTest.context(AuditWithoutDraftEditing.class)) {
            var db = ProductionInputHttpTest.db(context);
            var mvc = MaterialIssueHttpTest.mvc(context);
            WorkorderBomInputHttpTest.prepare(db, "100", "1", "2.5");
            WorkorderBomInputHttpTest.select(mvc, 1, 1);
            String uuid = UUID.randomUUID().toString();
            var saved = draft(mvc, ProductionInputHttpTest.command(mvc, uuid, "L001", "1"));
            context.getBean(AtomicBoolean.class).set(true);
            mvc.perform(full(post(StockDocumentHttpTest.ROOT + "/" + saved.get("document").get("id")
                            .asText() + "/actions/approve"))
                            .content(StockDocumentHttpTest.intent(uuid, saved.get("document").get("revision").asInt())))
                    .andExpect(status().isOk());
            BigDecimal after = rawBalance(db);
            int documentsBeforeDeniedSave = count(db, "wm_stock_document");
            int linesBeforeDeniedSave = count(db, "wm_stock_document_line");
            int actionsBeforeDeniedSave = count(db, "wm_stock_action");
            int inputsBeforeDeniedSave = count(db, "mes_material_issue_input WHERE confirmed_at IS NOT NULL");
            mvc.perform(full(post(StockDocumentHttpTest.ROOT + "/aggregate"))
                            .content(genericAggregate()))
                    .andExpect(status().isForbidden());
            assertEquals(documentsBeforeDeniedSave, count(db, "wm_stock_document"));
            assertEquals(linesBeforeDeniedSave, count(db, "wm_stock_document_line"));
            assertEquals(actionsBeforeDeniedSave, count(db, "wm_stock_action"));
            assertEquals(inputsBeforeDeniedSave, count(db, "mes_material_issue_input WHERE confirmed_at IS NOT NULL"));
            assertEquals(0, after.compareTo(rawBalance(db)));
        }
    }

    @Test
    void creationAndApprovalRequestIdentitiesUseIndependentNamespaces() throws Exception {
        try (var context = ProductionInputHttpTest.context()) {
            var db = ProductionInputHttpTest.db(context);
            var mvc = MaterialIssueHttpTest.mvc(context);
            WorkorderBomInputHttpTest.prepare(db, "100", "1", "2.5");
            WorkorderBomInputHttpTest.select(mvc, 1, 1);
            String uuid = UUID.randomUUID().toString();
            var reserved = draft(mvc, ProductionInputHttpTest.command(mvc, uuid, "L001", "1"));
            String other = StockDocumentHttpTest.create(mvc, "OTHER_RECEIPT", null).get("document").get("id").asText();
            StockDocumentHttpTest.add(mvc, other, "0", "2", "L002", "10");
            BigDecimal before = db.queryForObject("SELECT quantity FROM wm_inventory WHERE id=2", BigDecimal.class);
            String approval = StockDocumentHttpTest.intent(uuid, 1);
            mvc.perform(full(post(StockDocumentHttpTest.ROOT + "/" + other + "/actions/approve"))
                    .content(approval)).andExpect(status().isOk());
            mvc.perform(full(post(StockDocumentHttpTest.ROOT + "/" + other + "/actions/approve"))
                    .content(approval)).andExpect(status().isOk());
            assertEquals(0, before.add(new BigDecimal("10")).compareTo(
                    db.queryForObject("SELECT quantity FROM wm_inventory WHERE id=2", BigDecimal.class)));
            assertEquals(1, count(db, "wm_stock_action"));
            assertEquals(0, count(db, "mes_material_issue_input"));
            assertEquals("DRAFT", db.queryForObject("SELECT status FROM wm_stock_document WHERE id=?",
                    String.class, reserved.get("document").get("id").asText()));
            assertEquals(uuid, db.queryForObject("SELECT create_request_id FROM wm_stock_document WHERE id=?",
                    String.class, reserved.get("document").get("id").asText()));
        }
    }

    @Test
    void simultaneousCrossOrderDraftsCannotClaimTheSameRequestIdentity() throws Exception {
        try (var context = ProductionInputHttpTest.context(); var pool = Executors.newFixedThreadPool(2)) {
            var db = ProductionInputHttpTest.db(context);
            var mvc = MaterialIssueHttpTest.mvc(context);
            // TEST ONLY fixture uses the V62 request uniqueness constraint; live migration evidence is separate.
            WorkorderBomInputHttpTest.prepare(db, "100", "1", "2.5");
            WorkorderBomInputHttpTest.select(mvc, 1, 1);
            db.update("INSERT INTO workorder(id,code,product_id,planned_qty,routing_id,status,revision,create_time,update_time) VALUES(2,'WO2',99,100,1,'RELEASED',0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");
            db.update("INSERT INTO mes_workorder_material(id,material_id,unit_id,total_required_qty,planned_qty,warehouse_ids,work_center,revision,status,updated_by,updated_at,bom_id,bom_base_qty,bom_component_qty,output_unit_id) SELECT 2,material_id,unit_id,total_required_qty,planned_qty,warehouse_ids,work_center,revision,status,updated_by,updated_at,bom_id,bom_base_qty,bom_component_qty,output_unit_id FROM mes_workorder_material WHERE id=1");
            String uuid = UUID.randomUUID().toString();
            String first = ProductionInputHttpTest.command(mvc, uuid, "L001", "1");
            var second = (ObjectNode) JSON.readTree(first);
            var calculation = JSON.readTree(mvc.perform(full(get(ProductionInputHttpTest.ROOT + "/calculate"))
                            .param("code", "WO2:M1#L001").param("workorderId", "2").param("stockReference", "1")
                            .param("outputQty", "1"))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
            second.put("code", "WO2:M1#L001");
            second.put("workorderId", "2");
            second.put("calculationKey", calculation.get("calculationKey").asText());
            String secondBody = JSON.writeValueAsString(second);
            var start = new CountDownLatch(1);
            var one = pool.submit(() -> {
                start.await();
                return mvc.perform(full(post("/api/production/input/drafts")).content(first)).andReturn().getResponse()
                        .getStatus();
            });
            var two = pool.submit(() -> {
                start.await();
                return mvc.perform(full(post("/api/production/input/drafts")).content(secondBody)).andReturn()
                        .getResponse().getStatus();
            });
            start.countDown();
            assertEquals(List.of(200, 409), Stream.of(one.get(), two.get()).sorted().toList());
            assertEquals(1, count(db, "wm_stock_document"));
            assertEquals(0, count(db, "wm_stock_action"));
            assertEquals(0, count(db, "mes_production_execution"));
        }
    }

    @Test
    void unreadableFrozenBasisKeepsDocumentFactsVisibleAndStrictlyRejectsApproval() throws Exception {
        try (var context = ProductionInputHttpTest.context()) {
            var db = ProductionInputHttpTest.db(context);
            var mvc = MaterialIssueHttpTest.mvc(context);
            WorkorderBomInputHttpTest.prepare(db, "100", "1", "2.5");
            WorkorderBomInputHttpTest.select(mvc, 1, 1);
            String uuid = UUID.randomUUID().toString();
            var created = draft(mvc, ProductionInputHttpTest.command(mvc, uuid, "L001", "10"));
            String id = created.get("document").get("id").asText();
            BigDecimal before = rawBalance(db);
            for (String corrupt : new String[]{"{invalid", "null", "{}"}) {
                // Fixture-only corrupted source; the ordinary HTTP editor cannot alter the frozen JSON.
                db.update("UPDATE wm_stock_document SET source_request_json=? WHERE id=?", corrupt, id);
                mvc.perform(full(get(StockDocumentHttpTest.ROOT + "/" + id)))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("document.id").value(id))
                        .andExpect(jsonPath("document.status").value("DRAFT"))
                        .andExpect(jsonPath("lines.length()").value(1))
                        .andExpect(jsonPath("materialIssueBasis").isEmpty())
                        .andExpect(jsonPath("materialIssueBasisError").isNotEmpty());
                mvc.perform(full(post(StockDocumentHttpTest.ROOT + "/" + id + "/actions/approve"))
                                .content(StockDocumentHttpTest.intent(uuid, created.get("document").get("revision").asInt())))
                        .andExpect(status().is4xxClientError())
                        .andExpect(jsonPath("errorCode").isNotEmpty());
                assertEquals(0, count(db, "mes_production_execution"));
                assertEquals(0, count(db, "mes_material_issue_input WHERE confirmed_at IS NOT NULL"));
                assertEquals(0, count(db, "wm_stock_action"));
                assertEquals(0, before.compareTo(rawBalance(db)));
            }
        }
    }

    @Configuration
    static class NoAuditPermission {
        @Bean
        @Primary
        AuthorizationService denyAudit() {
            return (actor, scope, permission) -> !permission.code().equals("business:wm:stock:document:post");
        }
    }

    @Configuration
    static class NoCreatePermission {
        @Bean
        @Primary
        AuthorizationService denyCreate() {
            return (actor, scope, permission) -> !permission.code().equals("business:wm:stock:document:create");
        }
    }

    @Test
    void productionIssuePermissionCannotApproveAnUnrelatedStockDocument() throws Exception {
        try (var context = ProductionInputHttpTest.context(NoAuditPermission.class)) {
            var db = ProductionInputHttpTest.db(context);
            var mvc = MaterialIssueHttpTest.mvc(context);
            String other = StockDocumentHttpTest.create(mvc, "OTHER_RECEIPT", null).get("document").get("id").asText();
            StockDocumentHttpTest.add(mvc, other, "0", "2", "L002", "10");
            mvc.perform(full(post(StockDocumentHttpTest.ROOT + "/" + other + "/actions/approve"))
                            .content(StockDocumentHttpTest.intent(UUID.randomUUID().toString(), 1)))
                    .andExpect(status().isForbidden());
            assertEquals("DRAFT", db.queryForObject("SELECT status FROM wm_stock_document WHERE id=?", String.class, other));
            assertEquals(0, count(db, "wm_stock_action"));
        }
    }

    @Test
    void productionIssuePermissionCannotCreateAnUnrelatedStockDocument() throws Exception {
        try (var context = ProductionInputHttpTest.context(NoCreatePermission.class)) {
            var db = ProductionInputHttpTest.db(context);
            var mvc = MaterialIssueHttpTest.mvc(context);
            WorkorderBomInputHttpTest.prepare(db, "100", "1", "2.5");
            WorkorderBomInputHttpTest.select(mvc, 1, 1);
            String command = ProductionInputHttpTest.command(mvc, UUID.randomUUID().toString(), "L001", "10");
            mvc.perform(full(post(StockDocumentHttpTest.ROOT + "/aggregate"))
                            .content(genericAggregate()))
                    .andExpect(status().isForbidden());
            assertEquals(0, count(db, "wm_stock_document"));
            assertEquals(0, count(db, "wm_stock_document_line"));
            assertEquals(0, count(db, "wm_stock_action"));
            draft(mvc, command);
            assertEquals(1, count(db, "wm_stock_document"));
            assertEquals(0, count(db, "mes_material_issue_input WHERE confirmed_at IS NOT NULL"));
            assertEquals(0, count(db, "wm_stock_action"));
        }
    }

    private static JsonNode draft(MockMvc mvc, String command) throws Exception {
        return JSON.readTree(mvc.perform(full(post("/api/production/input/drafts")).content(command))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private static String genericAggregate() {
        return GENERIC_AGGREGATE;
    }

    private static int count(JdbcTemplate db, String table) {
        return db.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
    }

    private static BigDecimal rawBalance(JdbcTemplate db) {
        return db.queryForObject("SELECT quantity FROM wm_inventory WHERE id=1", BigDecimal.class);
    }

    @Test
    void draftIsInventoryNeutralApprovalCreatesOneAuditedIssueAndFrozenReplayRejectsChanges() throws Exception {
        try (var context = ProductionInputHttpTest.context()) {
            var db = ProductionInputHttpTest.db(context);
            var mvc = MaterialIssueHttpTest.mvc(context);
            WorkorderBomInputHttpTest.prepare(db, "100", "1", "2.5");
            WorkorderBomInputHttpTest.select(mvc, 1, 1);
            String uuid = UUID.randomUUID().toString();
            String command = ProductionInputHttpTest.command(mvc, uuid, "L001", "10");
            BigDecimal before = rawBalance(db);
            var created = draft(mvc, command);
            String id = created.get("document").get("id").asText();
            assertEquals(id, draft(mvc, command).get("document").get("id").asText());
            assertEquals(1, count(db, "wm_stock_document"));
            assertEquals("MATERIAL_ISSUE", created.get("document").get("documentType").asText());
            assertEquals("DRAFT", created.get("document").get("status").asText());
            assertEquals(0, before.compareTo(rawBalance(db)));
            assertEquals(0, count(db, "mes_production_execution"));
            assertEquals(0, count(db, "wm_stock_action"));
            String approval = StockDocumentHttpTest.intent(uuid, created.get("document").get("revision").asInt());
            String path = StockDocumentHttpTest.ROOT + "/" + id + "/actions/approve";
            mvc.perform(full(post(path)).content(approval)).andExpect(status().isOk());
            assertEquals(0, before.subtract(new BigDecimal("25")).compareTo(rawBalance(db)));
            assertEquals(1, count(db, "mes_production_execution"));
            assertEquals(1, count(db, "wm_stock_action"));
            var posted = db.queryForMap("SELECT status,posted_by,posted_at,post_request_id FROM wm_stock_document WHERE id=?", id);
            assertEquals("POSTED", posted.get("status"));
            assertEquals(1L, ((Number) posted.get("posted_by")).longValue());
            assertFalse(posted.get("posted_at") == null);
            assertEquals(uuid, posted.get("post_request_id"));
            mvc.perform(full(post(path)).content(approval)).andExpect(status().isOk());
            ProductionInputHttpTest.submit(mvc, command);
            assertEquals(1, count(db, "wm_stock_document"));
            assertEquals(1, count(db, "wm_stock_action"));
            var changed = (ObjectNode) JSON.readTree(command);
            changed.put("outputQty", "11");
            mvc.perform(full(post(ProductionInputHttpTest.ROOT)).content(JSON.writeValueAsString(changed)))
                    .andExpect(status().isConflict());
            assertEquals(1, count(db, "wm_stock_action"));
        }
    }

    @Test
    void stockLostAfterDraftRejectsApprovalWithoutInputOrExecutionAndLeavesDraftRetryable() throws Exception {
        try (var context = ProductionInputHttpTest.context()) {
            var db = ProductionInputHttpTest.db(context);
            var mvc = MaterialIssueHttpTest.mvc(context);
            WorkorderBomInputHttpTest.prepare(db, "100", "1", "2.5");
            WorkorderBomInputHttpTest.select(mvc, 1, 1);
            String uuid = UUID.randomUUID().toString();
            var created = draft(mvc, ProductionInputHttpTest.command(mvc, uuid, "L001", "10"));
            String id = created.get("document").get("id").asText();
            // Fixture-only competing consumption between save and approve; no production database is used.
            db.update("UPDATE wm_inventory SET quantity=0,available_quantity=0 WHERE id=1");
            mvc.perform(full(post(StockDocumentHttpTest.ROOT + "/" + id + "/actions/approve"))
                            .content(StockDocumentHttpTest.intent(uuid, created.get("document").get("revision").asInt())))
                    .andExpect(status().isBadRequest());
            assertEquals(0, count(db, "wm_stock_action"));
            assertEquals(0, count(db, "mes_production_execution"));
            assertEquals("DRAFT", db.queryForObject("SELECT status FROM wm_stock_document WHERE id=?", String.class, id));
            assertEquals(0, db.queryForObject("SELECT COUNT(*) FROM mes_material_issue_input WHERE confirmed_at IS NOT NULL", Integer.class));
        }
    }

    @Test
    void productionInboundConvenienceUsesFormalApprovalReplaysAndPreservesSplitClosureFacts() throws Exception {
        try (var context = ProductionInputHttpTest.context()) {
            var db = ProductionInputHttpTest.db(context);
            var mvc = MaterialIssueHttpTest.mvc(context);
            WorkorderBomInputHttpTest.prepare(db, "2", "1", "2.5");
            WorkorderBomInputHttpTest.select(mvc, 1, 1);
            db.update("INSERT INTO wm_inventory(id,material_id,warehouse_id,batch_no,quantity,available_quantity,status) VALUES(10,99,1,'L001',0,0,'ENABLED')");
            long execution = WorkorderBomInputHttpTest.input(mvc, "1");
            mvc.perform(full(post("/api/production/executions/" + execution + "/actions/start")))
                    .andExpect(status().isOk());
            WorkorderBomInputHttpTest.report(mvc, execution, "1");
            String path = "/api/production/executions/" + execution + "/inbound";
            int beforeDocuments = count(db, "wm_stock_document");
            mvc.perform(full(post(path)).content(JSON.writeValueAsString(Map.of("requestId", UUID.randomUUID()
                            .toString(), "stockId", "10", "quantity", "2", "reason", "TEST ONLY excess"))))
                    .andExpect(status().isConflict());
            assertEquals(beforeDocuments, count(db, "wm_stock_document"));
            assertEquals(0, new BigDecimal("0").compareTo(db.queryForObject("SELECT quantity FROM wm_inventory WHERE id=10", BigDecimal.class)));
            assertEquals(0, count(db, "mes_execution_completion"));
            db.update("INSERT INTO wm_inventory(id,material_id,warehouse_id,batch_no,quantity,available_quantity,status) VALUES(20,99,1,'l001',0,0,'ENABLED')");
            mvc.perform(full(post(path)).content(JSON.writeValueAsString(Map.of("requestId", UUID.randomUUID()
                            .toString(), "stockId", "20", "quantity", "1", "reason", "TEST ONLY wrong batch"))))
                    .andExpect(status().isConflict());
            assertEquals(beforeDocuments, count(db, "wm_stock_document"));
            String command = JSON.writeValueAsString(Map.of("requestId", UUID.randomUUID()
                    .toString(), "stockId", "10", "quantity", "1", "reason", "TEST ONLY unified approval"));
            mvc.perform(full(post(path)).content(command)).andExpect(status().isOk());
            var firstEnd = db.queryForMap("SELECT * FROM mes_execution_completion WHERE execution_id=?", execution);
            mvc.perform(full(post(path)).content(command)).andExpect(status().isOk());
            var changed = JSON.readTree(command).deepCopy();
            ((ObjectNode) changed).put("reason", "changed UUID content");
            mvc.perform(full(post(path)).content(JSON.writeValueAsString(changed))).andExpect(status().isConflict());
            assertEquals(1, count(db, "mes_execution_completion"));
            assertEquals(execution, WorkorderBomInputHttpTest.input(mvc, "1"));
            assertEquals("RUNNING", db.queryForObject("SELECT status FROM mes_production_execution WHERE id=?", String.class, execution));
            assertEquals(firstEnd, db.queryForMap("SELECT * FROM mes_execution_completion WHERE execution_id=?", execution));
            WorkorderBomInputHttpTest.report(mvc, execution, "1");
            mvc.perform(full(post(path)).content(JSON.writeValueAsString(Map.of("requestId", UUID.randomUUID()
                            .toString(), "stockId", "10", "quantity", "1", "reason", "TEST ONLY second receipt"))))
                    .andExpect(status().isOk());
            assertEquals(2, count(db, "mes_execution_completion"));
            assertEquals("COMPLETED", db.queryForObject("SELECT status FROM workorder WHERE id=1", String.class));
            assertEquals(0, new BigDecimal("2").compareTo(db.queryForObject("SELECT quantity FROM wm_inventory WHERE id=10", BigDecimal.class)));
            assertEquals(2, db.queryForObject("SELECT COUNT(*) FROM wm_stock_document WHERE document_type='PRODUCTION_RECEIPT' AND status='POSTED' AND posted_by=1 AND posted_at IS NOT NULL", Integer.class));
        }
    }
}
