package dev.issuefixture;

import dev.mesbase.core.security.AuthorizationService;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static dev.issuefixture.ProductionCoreV1HttpTest.full;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Read-only recovery uses the real transaction/statement guards in GuardedCenter.
 */
class StockDocumentRequestRecoveryHttpTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String ROOT = StockDocumentHttpTest.OUT_ROOT;

    enum Access {FULL, ISSUE_QUERY, NONE}

    @Configuration
    static class Permissions {
        @Bean
        AtomicReference<Access> recoveryAccess() {
            return new AtomicReference<>(Access.FULL);
        }

        @Bean
        @Primary
        AuthorizationService recoveryAuthorization(AtomicReference<Access> access) {
            return (actor, scope, permission) -> access.get() == Access.FULL
                    || access.get() == Access.ISSUE_QUERY && "business:mes:material:issue:query".equals(permission.code());
        }
    }

    @Test
    void mesRequestUuidRecoversThePostedDocumentWithoutAllocatingOrWriting() throws Exception {
        try (var context = ProductionInputHttpTest.context(StockDocumentOwnedTransactionsHttpTest.GuardedCenter.class, Permissions.class)) {
            var mvc = MaterialIssueHttpTest.mvc(context);
            var db = ProductionInputHttpTest.db(context);
            var numbers = context.getBean(AtomicInteger.class);
            WorkorderBomInputHttpTest.prepare(db, "100", "1", "2.5");
            WorkorderBomInputHttpTest.select(mvc, 1, 1);
            String creating = UUID.randomUUID().toString();
            String command = ProductionInputHttpTest.command(mvc, creating, "L001", "1");
            var input = JSON.readTree(ProductionInputHttpTest.submit(mvc, command));
            String id = input.get("documentId").asText();
            var before = snapshot(db);
            int allocationCount = numbers.get();
            var recovered = JSON.readTree(mvc.perform(full(get(ROOT + "/by-request/" + creating)))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
            assertEquals(id, recovered.get("document").get("id").asText());
            assertNoSecrets(recovered);
            assertEquals(before, snapshot(db));
            assertEquals(allocationCount, numbers.get());
            var replay = JSON.readTree(ProductionInputHttpTest.submit(mvc, command));
            assertEquals(id, replay.get("documentId").asText());
            assertEquals(before, snapshot(db));
            assertEquals(creating, db.queryForObject("SELECT id FROM mes_material_issue_input WHERE document_id=?", String.class, id));
            assertEquals(creating, db.queryForObject("SELECT post_request_id FROM wm_stock_document WHERE id=?", String.class, id));

            // A migrated input can have the original UUID only on its retained production fact.
            db.update("UPDATE wm_stock_document SET create_request_id=NULL WHERE id=?", id);
            before = snapshot(db);
            allocationCount = numbers.get();
            recovered = JSON.readTree(mvc.perform(full(get(ROOT + "/by-request/" + creating)))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
            assertEquals(id, recovered.get("document").get("id").asText());
            assertEquals("POSTED", recovered.get("document").get("status").asText());
            assertNoSecrets(recovered);
            assertEquals(before, snapshot(db));
            assertEquals(allocationCount, numbers.get());
        }
    }

    @Test
    void recoveryRetainsTypeAndPermissionBoundaries() throws Exception {
        try (var context = ProductionInputHttpTest.context(StockDocumentOwnedTransactionsHttpTest.GuardedCenter.class, Permissions.class)) {
            var mvc = MaterialIssueHttpTest.mvc(context);
            var db = ProductionInputHttpTest.db(context);
            var access = context.getBean(AtomicReference.class);
            String request = UUID.randomUUID().toString();
            var input = JSON.readTree(ProductionInputHttpTest.submit(mvc, ProductionInputHttpTest.command(mvc, request, "L001", "10")));
            String id = input.get("documentId").asText();
            var other = StockDocumentHttpTest.create(mvc, "OTHER_RECEIPT", null);
            String otherRequest = UUID.randomUUID().toString();
            db.update("UPDATE wm_stock_document SET create_request_id=? WHERE id=?", otherRequest, other.get("document")
                    .get("id").asText());
            var before = snapshot(db);
            int numberCount = context.getBean(AtomicInteger.class).get();
            access.set(Access.ISSUE_QUERY);
            var result = JSON.readTree(mvc.perform(full(get(ROOT + "/by-request/" + request)))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
            assertEquals(id, result.get("document").get("id").asText());
            assertEquals(0, result.get("lines").size());
            assertNoSecrets(result);
            mvc.perform(full(get(ROOT + "/by-request/" + otherRequest))).andExpect(status().isNotFound());
            mvc.perform(full(get(ROOT + "/by-request/" + UUID.randomUUID()))).andExpect(status().isNotFound());
            mvc.perform(full(get(ROOT + "/by-request/not-a-uuid"))).andExpect(status().isBadRequest());
            access.set(Access.NONE);
            mvc.perform(full(get(ROOT + "/by-request/" + request))).andExpect(status().isForbidden());
            mvc.perform(full(get(ROOT + "/by-request/not-a-uuid"))).andExpect(status().isForbidden());
            access.set(Access.FULL);
            mvc.perform(full(get(ROOT + "/by-request/" + otherRequest))).andExpect(status().isNotFound());
            // A corrupted production association cannot turn this recovery entry into another-type reader.
            db.update("UPDATE mes_material_issue_input SET document_id=? WHERE id=?", other.get("document").get("id")
                    .asText(), request);
            mvc.perform(full(get(ROOT + "/by-request/" + request))).andExpect(status().isNotFound());
            access.set(Access.ISSUE_QUERY);
            mvc.perform(full(get(ROOT + "/by-request/" + request))).andExpect(status().isNotFound());
            db.update("UPDATE mes_material_issue_input SET document_id=? WHERE id=?", id, request);
            assertEquals(before, snapshot(db));
            assertEquals(numberCount, context.getBean(AtomicInteger.class).get());
        }
    }

    private static List<List<Map<String, Object>>> snapshot(JdbcTemplate jdbc) {
        return List.of("wm_stock_document", "wm_stock_document_line", "wm_stock_action", "wm_inventory", "mes_material_issue_input",
                        "mes_production_execution", "mes_execution_path", "mes_execution_path_node")
                .stream().map(table -> jdbc.queryForList("SELECT * FROM " + table + " ORDER BY id")).toList();
    }

    private static void assertNoSecrets(JsonNode response) {
        String text = response.toString().toLowerCase(Locale.ROOT);
        for (String key : List.of("requestHash", "request_hash", "calculationJson", "calculation_json", "sourceRequestJson", "source_request_json"))
            assertFalse(text.contains(key.toLowerCase(Locale.ROOT)), "Recovery must not expose frozen protocol data: " + key);
    }
}
