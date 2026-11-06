package dev.issuefixture;

import dev.mesbase.acceptance.FactorySnapshotFixture;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static dev.issuefixture.ProductionCoreV1HttpTest.full;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Real Spring/SQL HTTP regression on isolated H2; live MySQL acceptance is separate.
 */
class PurchaseReceiptHttpTest {
    static String create(String supplier, String items) {
        return "{\"header\":{\"direction\":\"IN\",\"documentType\":\"PURCHASE_RECEIPT\",\"reason\":\"TEST ONLY direct delivery\",\"ruleCode\":\"PURCHASE_RECEIPT\",\"supplierName\":\"" + supplier
                + "\",\"supplierDocumentNo\":\"TEST-DELIVERY\"},\"items\":[" + items + "]}";
    }

    static String item(String stock, String batch, String quantity) {
        return "{\"stockId\":\"" + stock + "\",\"materialId\":\"1\",\"warehouseId\":\"1\",\"unitId\":\"1\",\"batchNo\":\"" + batch + "\",\"binCode\":\"BIN1\",\"quantity\":\"" + quantity + "\"}";
    }

    @Test
    void supplierSnapshotMultiBatchReplayAndPostedProtection() throws Exception {
        try (var c = ProductionInputHttpTest.context()) {
            var m = MaterialIssueHttpTest.mvc(c);
            var db = ProductionInputHttpTest.db(c);
            db.update("UPDATE wm_inventory SET quantity=0,available_quantity=0 WHERE id=1");
            db.update("INSERT INTO wm_inventory(id,material_id,warehouse_id,batch_no,bin_code,quantity,available_quantity,status) VALUES(9,1,1,'B2','BIN1',0,0,'ENABLED')");
            var result = StockDocumentHttpTest.JSON.readTree(m.perform(full(post(StockDocumentHttpTest.ROOT + "/aggregate"))
                            .content(create("TEST Supplier", item("1", "L001", "2") + "," + item("9", "B2", "3"))))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
            var beforeB = c.getBean(MaterialIssueHttpTest.Fixture.class).business.get("B")
                    .queryForObject("SELECT quantity FROM wm_inventory WHERE id=1", java.math.BigDecimal.class);
            var header = result.get("document");
            String id = header.get("id").asText();
            m.perform(get(StockDocumentHttpTest.ROOT + "/" + id).header("Authorization", "Bearer FULL")
                            .header("X-Factory-Id", "B"))
                    .andExpect(status().isNotFound());
            assertEquals("TEST Supplier", header.get("supplierName").asText());
            assertEquals("TEST-DELIVERY", header.get("supplierDocumentNo").asText());
            StockDocumentHttpTest.balance(db, "0");
            String intent = StockDocumentHttpTest.intent(UUID.randomUUID().toString(), header.get("revision").asInt());
            for (int i = 0; i < 2; i++)
                m.perform(full(post(StockDocumentHttpTest.ROOT + "/" + id + "/actions/approve")).content(intent))
                        .andExpect(status().isOk());
            StockDocumentHttpTest.balance(db, "2");
            assertEquals(0, new java.math.BigDecimal("3").compareTo(db.queryForObject("SELECT quantity FROM wm_inventory WHERE id=9", java.math.BigDecimal.class)));
            assertEquals(2, db.queryForObject("SELECT COUNT(*) FROM wm_stock_action WHERE document_line_id IS NOT NULL", Integer.class));
            assertEquals(beforeB, c.getBean(MaterialIssueHttpTest.Fixture.class).business.get("B")
                    .queryForObject("SELECT quantity FROM wm_inventory WHERE id=1", java.math.BigDecimal.class));
            m.perform(full(delete(StockDocumentHttpTest.ROOT + "/" + id)).content("{\"expectedRevision\":3}"))
                    .andExpect(status().isMethodNotAllowed());
            m.perform(full(put(StockDocumentHttpTest.ROOT + "/" + id)).content("{\"expectedRevision\":3,\"reason\":\"change\",\"supplierName\":\"changed\"}"))
                    .andExpect(status().isConflict());
        }
    }

    @Test
    void invalidIdentityQuantitySupplierAndErpCannotPost() throws Exception {
        try (var c = ProductionInputHttpTest.context()) {
            var m = MaterialIssueHttpTest.mvc(c);
            var db = ProductionInputHttpTest.db(c);
            db.update("UPDATE wm_inventory SET quantity=0,available_quantity=0 WHERE id=1");
            var draft = StockDocumentHttpTest.JSON.readTree(m.perform(full(post(StockDocumentHttpTest.ROOT + "/aggregate"))
                            .content(create("", item("1", "L001", "2")))).andExpect(status().isOk()).andReturn().getResponse()
                    .getContentAsString()).get("document");
            m.perform(full(post(StockDocumentHttpTest.ROOT + "/" + draft.get("id").asText() + "/actions/approve"))
                            .content(StockDocumentHttpTest.intent(UUID.randomUUID().toString(), draft.get("revision").asInt())))
                    .andExpect(status().isBadRequest());
            m.perform(full(post(StockDocumentHttpTest.ROOT + "/aggregate")).content(create("TEST", item("1", "L001", "-1"))))
                    .andExpect(status().isBadRequest());
            for (String bad : new String[]{item("1", "WRONG", "2"), item("1", "L001", "2").replace("\"materialId\":\"1\"", "\"materialId\":\"999999\""), item("1", "L001", "2").replace("\"warehouseId\":\"1\"", "\"warehouseId\":\"999999\"")})
                m.perform(full(post(StockDocumentHttpTest.ROOT + "/aggregate")).content(create("TEST", bad)))
                        .andExpect(status().is(bad.contains("\"warehouseId\":\"999999\"") ? 422 : 409));
            FactorySnapshotFixture.set(c, FactorySnapshotFixture.erp("A", "E10", "TEST"));
            m.perform(full(post(StockDocumentHttpTest.ROOT + "/aggregate")).content(create("TEST", item("1", "L001", "2"))))
                    .andExpect(status().isConflict());
            StockDocumentHttpTest.balance(db, "0");
            assertEquals(0, db.queryForObject("SELECT COUNT(*) FROM wm_stock_action", Integer.class));
        }
    }

    @Test
    void draftSupplierCanBeEditedWithoutChangingStock() throws Exception {
        try (var c = ProductionInputHttpTest.context()) {
            var m = MaterialIssueHttpTest.mvc(c);
            var db = ProductionInputHttpTest.db(c);
            var before = db.queryForObject("SELECT quantity FROM wm_inventory WHERE id=1", java.math.BigDecimal.class);
            var document = StockDocumentHttpTest.JSON.readTree(m.perform(full(post(StockDocumentHttpTest.ROOT + "/aggregate"))
                            .content(create("Supplier One", item("1", "L001", "2"))))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).get("document");
            var changed = StockDocumentHttpTest.JSON.readTree(m.perform(full(put(StockDocumentHttpTest.ROOT + "/" + document.get("id")
                            .asText()))
                            .content("{\"expectedRevision\":" + document.get("revision")
                                    .asText() + ",\"reason\":\"updated delivery\",\"supplierName\":\"Supplier Two\",\"supplierDocumentNo\":\"DELIVERY-TWO\"}"))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).get("document");
            assertEquals("Supplier Two", changed.get("supplierName").asText());
            assertEquals("DELIVERY-TWO", changed.get("supplierDocumentNo").asText());
            assertEquals(before, db.queryForObject("SELECT quantity FROM wm_inventory WHERE id=1", java.math.BigDecimal.class));
            assertEquals(0, db.queryForObject("SELECT COUNT(*) FROM wm_stock_action", Integer.class));
        }
    }
}
