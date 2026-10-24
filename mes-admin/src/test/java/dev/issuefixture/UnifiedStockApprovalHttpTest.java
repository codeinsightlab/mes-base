package dev.issuefixture;

import dev.mesbase.core.security.AuthorizationService;
import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Independent isolated H2 HTTP/SQL transaction regression; never a deployed MySQL acceptance.
 */
class UnifiedStockApprovalHttpTest {
    private static final ObjectMapper JSON = new ObjectMapper();

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
    void businessPermissionsCannotReplaceTheFormalAuditPermission() throws Exception {
        assertConveniencePermissionRequired(NoAuditPermission.class);
    }

    @Test
    void businessPermissionsCannotReplaceTheFormalDocumentCreatePermission() throws Exception {
        assertConveniencePermissionRequired(NoCreatePermission.class);
    }

    private static void assertConveniencePermissionRequired(Class<?> configuration) throws Exception {
        try (var context = ProductionInputHttpTest.context(configuration)) {
            var db = ProductionInputHttpTest.db(context);
            var mvc = MaterialIssueHttpTest.mvc(context);
            WorkorderBomInputHttpTest.prepare(db, "100", "1", "2.5");
            WorkorderBomInputHttpTest.select(mvc, 1, 1);
            String command = ProductionInputHttpTest.command(mvc, UUID.randomUUID().toString(), "L001", "10");
            // FULL is admitted by the HTTP business permission; domain authorization explicitly denies audit.
            mvc.perform(full(post(ProductionInputHttpTest.ROOT)).content(command)).andExpect(status().isForbidden());
            mvc.perform(full(post("/api/production/executions/999/inbound")).content(JSON.writeValueAsString(Map.of(
                            "requestId", UUID.randomUUID()
                                    .toString(), "stockId", "1", "quantity", "1", "reason", "TEST ONLY permission"))))
                    .andExpect(status().isForbidden());
            assertEquals(0, count(db, "wm_stock_document"));
            assertEquals(0, count(db, "wm_stock_action"));
            assertEquals(0, count(db, "mes_production_execution"));
        }
    }

    private static JsonNode draft(MockMvc mvc, String command) throws Exception {
        return JSON.readTree(mvc.perform(full(post("/api/production/input/drafts")).content(command))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
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
