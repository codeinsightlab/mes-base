package dev.issuefixture;

import dev.mesbase.core.security.AuthorizationService;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import tools.jackson.databind.ObjectMapper;

import static dev.issuefixture.ProductionCoreV1HttpTest.full;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Current MES orchestration permissions remain separate from ordinary warehouse permissions.
 */
class UnifiedStockApprovalHttpTest {
    @Configuration
    static class Permissions {
        @Bean
        AtomicBoolean restrictWarehouse() {
            return new AtomicBoolean();
        }

        @Bean
        @Primary
        AuthorizationService commandPermissions(AtomicBoolean restrictWarehouse) {
            return (actor, scope, permission) -> !restrictWarehouse.get()
                    || !List.of("business:wm:stock:document:create", "business:wm:stock:document:update", "business:wm:stock:document:post")
                    .contains(permission.code());
        }
    }

    @Test
    void mesPermissionPostsItsWholeIssueWithoutGrantingOrdinaryWarehouseAuthority() throws Exception {
        try (var c = ProductionInputHttpTest.context(Permissions.class)) {
            var j = ProductionInputHttpTest.db(c);
            var m = MaterialIssueHttpTest.mvc(c);
            String command = ProductionInputHttpTest.command(m, UUID.randomUUID().toString(), "L001", "10");
            c.getBean(AtomicBoolean.class).set(true);
            ProductionInputHttpTest.submit(m, command);
            m.perform(full(post(StockDocumentHttpTest.OUT_ROOT)).content("{\"direction\":\"OUT\",\"documentType\":\"MATERIAL_ISSUE\",\"ruleCode\":\"MATERIAL_ISSUE\",\"reason\":\"TEST ONLY denied\"}"))
                    .andExpect(status().isForbidden());
            assertEquals(1, j.queryForObject("SELECT COUNT(*) FROM wm_stock_document", Integer.class));
            assertEquals(1, j.queryForObject("SELECT COUNT(*) FROM mes_material_issue_input", Integer.class));
        }
    }

    @Test
    void ordinaryMaterialIssueApprovalRequiresWarehousePostPermission() throws Exception {
        try (var c = ProductionInputHttpTest.context(Permissions.class)) {
            var j = ProductionInputHttpTest.db(c);
            var m = MaterialIssueHttpTest.mvc(c);
            var draft = new ObjectMapper().readTree(m.perform(full(post(StockDocumentHttpTest.OUT_ROOT)).content("{\"direction\":\"OUT\",\"documentType\":\"MATERIAL_ISSUE\",\"ruleCode\":\"MATERIAL_ISSUE\",\"reason\":\"TEST ONLY ordinary draft\"}"))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).get("document");
            String id = draft.get("id").asText();
            m.perform(full(post(StockDocumentHttpTest.OUT_ROOT + "/" + id + "/lines")).content(StockDocumentHttpTest.line("0", "1", "L001", "10")))
                    .andExpect(status().isOk());
            c.getBean(AtomicBoolean.class).set(true);
            m.perform(full(post(StockDocumentHttpTest.OUT_ROOT + "/" + id + "/actions/approve")).content(StockDocumentHttpTest.intent(UUID.randomUUID()
                            .toString(), 1)))
                    .andExpect(status().isForbidden());
            assertEquals(0, j.queryForObject("SELECT COUNT(*) FROM wm_stock_action", Integer.class));
            assertEquals(0, j.queryForObject("SELECT COUNT(*) FROM mes_material_issue_input", Integer.class));
        }
    }

    @Test
    void damagedFrozenOldDraftStaysReadableButCannotEnterEitherPostingFlow() throws Exception {
        try (var c = ProductionInputHttpTest.context()) {
            var j = ProductionInputHttpTest.db(c);
            var m = MaterialIssueHttpTest.mvc(c);
            var draft = new ObjectMapper().readTree(m.perform(full(post(StockDocumentHttpTest.OUT_ROOT)).content("{\"direction\":\"OUT\",\"documentType\":\"MATERIAL_ISSUE\",\"ruleCode\":\"MATERIAL_ISSUE\",\"reason\":\"TEST ONLY old draft\"}"))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).get("document");
            String id = draft.get("id").asText();
            m.perform(full(post(StockDocumentHttpTest.OUT_ROOT + "/" + id + "/lines")).content(StockDocumentHttpTest.line("0", "1", "L001", "10")))
                    .andExpect(status().isOk());
            j.update("UPDATE wm_stock_document SET source_request_json='{invalid' WHERE id=?", id);
            m.perform(full(get(StockDocumentHttpTest.OUT_ROOT + "/" + id))).andExpect(status().isOk())
                    .andExpect(jsonPath("lines.length()").value(1))
                    .andExpect(jsonPath("materialIssueBasisError").isNotEmpty());
            m.perform(full(post(StockDocumentHttpTest.OUT_ROOT + "/" + id + "/actions/approve")).content(StockDocumentHttpTest.intent(UUID.randomUUID()
                    .toString(), 1))).andExpect(status().isUnprocessableEntity());
            m.perform(full(post("/api/production/input/drafts")).content("{}")).andExpect(status().isNotFound());
            assertEquals(0, j.queryForObject("SELECT COUNT(*) FROM mes_material_issue_input", Integer.class));
            assertEquals(0, j.queryForObject("SELECT COUNT(*) FROM wm_stock_action", Integer.class));
        }
    }
}
