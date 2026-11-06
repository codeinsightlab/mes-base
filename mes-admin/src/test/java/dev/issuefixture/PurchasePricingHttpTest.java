package dev.issuefixture;

import dev.mesbase.acceptance.FactorySnapshotFixture;
import org.junit.jupiter.api.Test;

import static dev.issuefixture.ProductionCoreV1HttpTest.full;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Routed H2 HTTP contract/isolation tests; real MySQL evidence is separate.
 */
class PurchasePricingHttpTest {
    static final String SUPPLIERS = "/api/purchase/purchase-supplier";
    static final String PRICES = "/api/purchase/purchase-reference-price";

    static String supplier(String code, String name, String state) {
        return "{\"code\":\"" + code + "\",\"name\":\"" + name + "\",\"status\":\"" + state + "\"}";
    }

    static String quote(String supplier, String price, String date) {
        return "{\"supplierId\":\"" + supplier + "\",\"materialId\":\"1\",\"unitId\":\"1\",\"unitPrice\":\"" + price + "\",\"currency\":\"CNY\",\"effectiveDate\":\"" + date + "\"}";
    }

    @Test
    void appendOnlyPricesEffectiveDatesIsolationAndSupplierGuards() throws Exception {
        try (var c = ProductionInputHttpTest.context()) {
            var mvc = MaterialIssueHttpTest.mvc(c);
            var json = StockDocumentHttpTest.JSON;
            var created = json.readTree(mvc.perform(full(post(SUPPLIERS)).content(supplier("QA_NEW", "新供应商", "ENABLED")))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
            String id = created.get("id").asText();
            assertEquals("新供应商", created.get("name").asText());
            assertNotNull(created.get("createdBy"));
            mvc.perform(full(post(SUPPLIERS)).content(supplier("QA_NEW", "重复编码", "ENABLED")))
                    .andExpect(status().isConflict());
            mvc.perform(full(post(PRICES)).content(quote(id, "1.111111", "2026-11-01"))).andExpect(status().isOk());
            var latest = json.readTree(mvc.perform(full(post(PRICES)).content(quote(id, "2.222222", "2026-11-01")))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
            mvc.perform(full(post(PRICES)).content(quote(id, "9", "2026-12-01"))).andExpect(status().isOk());
            mvc.perform(full(post(PRICES)).content(quote("2", "7", "2026-11-01"))).andExpect(status().isOk());
            var effective = json.readTree(mvc.perform(full(get(PRICES + "/effective")).param("supplierId", id)
                            .param("materialId", "1").param("date", "2026-11-06"))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
            assertEquals(latest.get("id"), effective.get("id"));
            var preview = json.readTree(mvc.perform(full(get("/api/purchase/receipt-price"))
                            .param("supplierId", id).param("materialId", "1").param("date", "2026-11-06")
                            .param("quantity", "3").param("unitPrice", "1.005"))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
            assertEquals("3.02", preview.get("amount").asText());
            var page = json.readTree(mvc.perform(full(get(PRICES)).param("supplierId", id))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
            assertEquals("新供应商", page.get("items").get(0).get("supplierName").asText());
            assertEquals("物料一", page.get("items").get(0).get("materialName").asText());
            assertEquals("千克", page.get("items").get(0).get("unitName").asText());

            assertEquals(3, ProductionInputHttpTest.db(c)
                    .queryForObject("SELECT COUNT(*) FROM purchase_reference_price WHERE supplier_id=?", Integer.class, id));
            mvc.perform(full(put(PRICES + "/" + latest.get("id").asText())).content(quote(id, "99", "2026-11-01")))
                    .andExpect(status().isMethodNotAllowed());
            mvc.perform(full(delete(PRICES)).content("[" + latest.get("id").asText() + "]"))
                    .andExpect(status().isMethodNotAllowed());
            mvc.perform(full(post(PRICES)).content(quote(id, "-1", "2026-11-01"))).andExpect(status().isBadRequest());
            mvc.perform(full(post(PRICES)).content(quote(id, "0", "2026-11-01"))).andExpect(status().isBadRequest());
            mvc.perform(full(post(PRICES)).content(quote(id, "1", "2026-11-01").replace("\"unitId\":\"1\"", "\"unitId\":\"999\"")))
                    .andExpect(status().isBadRequest());
            mvc.perform(full(delete(SUPPLIERS)).content("[" + id + "]")).andExpect(status().isBadRequest());
            mvc.perform(get(SUPPLIERS + "/" + id).header("Authorization", "Bearer FULL").header("X-Factory-Id", "B"))
                    .andExpect(status().isNotFound());
            var other = json.readTree(mvc.perform(get(PRICES).header("Authorization", "Bearer FULL")
                            .header("X-Factory-Id", "B").param("supplierId", id))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
            assertEquals(0, other.get("total").asInt());
            mvc.perform(full(put(SUPPLIERS + "/" + id)).content(supplier("QA_NEW", "改名停用", "DISABLED")))
                    .andExpect(status().isOk());
            mvc.perform(full(post(PRICES)).content(quote(id, "3", "2026-11-01"))).andExpect(status().isBadRequest());
            mvc.perform(full(post(StockDocumentHttpTest.ROOT + "/aggregate")).content(PurchaseReceiptHttpTest.create("ignored", PurchaseReceiptHttpTest.item("1", "L001", "1"))
                            .replace("\"supplierId\":\"1\"", "\"supplierId\":\"" + id + "\"")))
                    .andExpect(status().isBadRequest());
            var unused = json.readTree(mvc.perform(full(post(SUPPLIERS)).content(supplier("QA_DELETE", "未引用", "ENABLED")))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
            mvc.perform(full(delete(SUPPLIERS)).content("[" + unused.get("id").asText() + "]"))
                    .andExpect(status().isNoContent());
            FactorySnapshotFixture.set(c, FactorySnapshotFixture.erp("A", "E10", "TEST"));
            mvc.perform(full(post(PRICES)).content(quote("1", "3", "2026-11-01"))).andExpect(status().isConflict());
        }
    }
}
