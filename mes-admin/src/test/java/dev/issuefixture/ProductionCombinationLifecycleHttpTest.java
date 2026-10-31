package dev.issuefixture;

import dev.mesbase.business.production.controller.ProductionScanController;
import dev.mesbase.business.production.service.ProductionScanService;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import static dev.issuefixture.ProductionCoreV1HttpTest.full;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * TEST ONLY: current HTTP commands on routed H2; no seeded production input, reports or closure.
 */
class ProductionCombinationLifecycleHttpTest {
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void printedMultiMaterialCodeFlowsThroughScanReportingQualityPartialInboundAndFinalClosure() throws Exception {
        var extra = new ArrayList<Class<?>>(Arrays.asList(QualityDispositionHttpTest.EXTRA));
        extra.addAll(List.of(ProductionReportingHttpTest.Extra.class, ProductionDefectHttpTest.Extra.class,
                ProductionScanService.class, ProductionScanController.class));
        try (var c = ProductionInputHttpTest.context(extra.toArray(Class<?>[]::new))) {
            var j = ProductionInputHttpTest.db(c);
            var m = MaterialIssueHttpTest.mvc(c);
            j.update("DELETE FROM mes_execution_path_node");
            j.update("DELETE FROM mes_execution_path");
            j.update("DELETE FROM mes_production_execution");
            ProductionInputHttpTest.multiMaterial(j);
            j.update("UPDATE workorder SET planned_qty=50 WHERE id=1");
            j.update("UPDATE mes_workorder_material SET planned_qty=50,total_required_qty=200 WHERE id=1");
            j.update("UPDATE mes_workorder_material SET planned_qty=50,total_required_qty=50 WHERE id=2");
            // Both ordinary warehouse drafts and posted issues may reference this order.
            // Neither is MES input or a production closure gate.
            for (boolean approve : List.of(false, true)) {
                var ordinary = json.readTree(m.perform(full(post(StockDocumentHttpTest.OUT_ROOT + "/aggregate"))
                                .content("{\"header\":{\"direction\":\"OUT\",\"documentType\":\"MATERIAL_ISSUE\",\"ruleCode\":\"MATERIAL_ISSUE\",\"reason\":\"TEST ONLY ordinary issue\",\"sourceWorkorderId\":\"1\"},\"items\":[{\"stockId\":\"3\",\"materialId\":\"2\",\"warehouseId\":\"1\",\"unitId\":\"1\",\"batchNo\":\"C01\",\"binCode\":\"BIN2\",\"quantity\":\"1\"}]}"))
                        .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).get("document");
                if (approve)
                    m.perform(full(post(StockDocumentHttpTest.OUT_ROOT + "/" + ordinary.get("id")
                                    .asText() + "/actions/approve"))
                                    .content(StockDocumentHttpTest.intent(UUID.randomUUID().toString(), ordinary.get("revision")
                                            .asInt())))
                            .andExpect(status().isOk());
            }
            j.update("INSERT INTO mes_operation_user(operation_id,user_id) VALUES(1,1)");
            j.update("INSERT INTO wm_warehouse(id,code,name,status,source,warehouse_type) VALUES(2,'TEST-Q','TEST ONLY quality warehouse','ENABLED','LOCAL','DEFECTIVE')");
            var plan = ProductionInputHttpTest.generate(m, "100");
            String code = plan.get("combination").get("code").asText();
            String scan = json.writeValueAsString(Map.of("type", "REPORTING", "code", code, "inputMethod", "TEXT"));
            m.perform(full(post("/api/production/scan")).content(scan)).andExpect(status().isNotFound());
            assertEquals(1, j.queryForObject("SELECT COUNT(*) FROM wm_stock_action", Integer.class));
            assertEquals(0, j.queryForObject("SELECT COUNT(*) FROM mes_material_issue_input", Integer.class));
            var input = json.readTree(ProductionInputHttpTest.submit(m, ProductionInputHttpTest.confirmCommand(m, UUID.randomUUID()
                    .toString(), code, "50")));
            long execution = input.get("executionId").asLong();
            String document = input.get("documentId").asText();
            m.perform(full(post("/api/production/scan")).content(scan)).andExpect(status().isOk())
                    .andExpect(jsonPath("executionId").value(Long.toString(execution)));
            for (String quantity : List.of("30", "20")) {
                for (long node : List.of(11L, 12L, 13L))
                    m.perform(full(post("/api/production/reporting")).content(json.writeValueAsString(Map.of("requestId", UUID.randomUUID()
                                    .toString(), "executionId", execution, "routingNodeId", node, "reportQty", quantity))))
                            .andExpect(status().isOk());
                if (quantity.equals("30")) WorkorderBomInputHttpTest.receive(m, execution, "20");
            }
            String declaration = ProductionDefectHttpTest.add(m, ProductionDefectHttpTest.command("TEST-COMBO-QUALITY", 0, "5")
                    .replace("executionId\":\"1", "executionId\":\"" + execution), "A");
            WorkorderBomInputHttpTest.receive(m, execution, "25");
            assertEquals("RUNNING", j.queryForObject("SELECT status FROM mes_production_execution WHERE id=?", String.class, execution));
            QualityDispositionHttpTest.dispose(m, declaration, QualityDispositionHttpTest.command(UUID.randomUUID()
                    .toString(), "WAREHOUSE", "5", "2"));
            assertEquals("FULLY_RECEIVED", j.queryForObject("SELECT status FROM mes_production_execution WHERE id=?", String.class, execution));
            assertEquals("COMPLETED", j.queryForObject("SELECT status FROM workorder WHERE id=1", String.class));
            assertEquals(1, j.queryForObject("SELECT COUNT(*) FROM mes_execution_completion WHERE execution_id=?", Integer.class, execution));
            assertEquals(0, new BigDecimal("50").compareTo(j.queryForObject("SELECT SUM(output_qty) FROM mes_material_issue_input WHERE stock_applied=TRUE", BigDecimal.class)));
            var before = j.queryForList("SELECT * FROM wm_stock_action ORDER BY id");
            m.perform(full(post(StockDocumentHttpTest.OUT_ROOT + "/" + document + "/actions/void")))
                    .andExpect(status().isConflict());
            assertEquals(before, j.queryForList("SELECT * FROM wm_stock_action ORDER BY id"));
            m.perform(full(get("/api/production/combinations/calculate")).param("code", code))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("errorCode").value("PRODUCTION_COMBINATION_CLOSED"));
            m.perform(full(get("/api/production/combinations/resolve")).param("code", code)).andExpect(status().isOk())
                    .andExpect(jsonPath("closed").value(true));
            m.perform(full(post("/api/production/combinations")).content("{\"workorderId\":\"1\",\"quantity\":\"50\"}"))
                    .andExpect(status().isConflict());
            m.perform(full(get("/api/production/combinations/" + plan.get("combination").get("id").asText() + "/qr")))
                    .andExpect(status().isOk());
            assertEquals(1, j.queryForObject("SELECT COUNT(*) FROM mes_production_execution WHERE batch_no IS NOT NULL", Integer.class));
        }
    }

    @Test
    void duplicateMaterialDemandSharesStockAndLineSpecificReturnAndWholeReversalStayTraceable() throws Exception {
        try (var c = ProductionInputHttpTest.context()) {
            var j = ProductionInputHttpTest.db(c);
            var m = MaterialIssueHttpTest.mvc(c);
            j.update("UPDATE unit_measure SET must_be_whole_number=1 WHERE id=1");
            j.update("UPDATE wm_inventory SET quantity=100,available_quantity=100 WHERE id=1");
            j.update("UPDATE wm_inventory SET quantity=0,available_quantity=0 WHERE id=2");
            j.update("INSERT INTO mes_workorder_material(id,workorder_id,line_no,material_id,unit_id,total_required_qty,planned_qty,manually_adjusted,updated_by,updated_at) VALUES(2,1,2,1,1,1000,1000,TRUE,1,CURRENT_TIMESTAMP)");
            var plan = ProductionInputHttpTest.generate(m, "100");
            String code = plan.get("combination").get("code").asText();
            assertEquals(0, new BigDecimal("33").compareTo(new BigDecimal(plan.get("suggestedQty").asText())));
            String confirmedCommand = ProductionInputHttpTest.confirmCommand(m, UUID.randomUUID()
                    .toString(), code, "20");
            var input = json.readTree(ProductionInputHttpTest.submit(m, confirmedCommand));
            long document = input.get("documentId").asLong();
            String request = UUID.randomUUID().toString();
            var back = new LinkedHashMap<String, Object>(Map.of("requestId", request, "stockId", "1", "quantity", "5", "expectedQuantity", "40", "expectedAvailable", "40", "reason", "TEST ONLY line return", "documentId", document));
            m.perform(full(post("/api/wm/local-stock/return")).content(json.writeValueAsString(back)))
                    .andExpect(status().isBadRequest());
            long line = j.queryForObject("SELECT id FROM wm_stock_document_line WHERE document_id=? AND demand_line_id=1", Long.class, document);
            back.put("documentLineId", line);
            for (int retry = 0; retry < 2; retry++)
                m.perform(full(post("/api/wm/local-stock/return")).content(json.writeValueAsString(back)))
                        .andExpect(status().isOk());
            assertEquals(0, new BigDecimal("45").compareTo(j.queryForObject("SELECT quantity FROM wm_inventory WHERE id=1", BigDecimal.class)));
            var response = m.perform(full(post(StockDocumentHttpTest.OUT_ROOT + "/" + document + "/actions/void")))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            m.perform(full(post(StockDocumentHttpTest.OUT_ROOT + "/" + document + "/actions/void")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("voidOperationId").value(json.readTree(response).get("voidOperationId")
                            .asText()));
            assertEquals(0, new BigDecimal("100").compareTo(j.queryForObject("SELECT quantity FROM wm_inventory WHERE id=1", BigDecimal.class)));
            assertEquals(2, j.queryForObject("SELECT COUNT(*) FROM wm_stock_action WHERE action_type='REVERSAL'", Integer.class));
            assertEquals(1, j.queryForObject("SELECT COUNT(DISTINCT operation_id) FROM wm_stock_action WHERE action_type='REVERSAL'", Integer.class));
            assertEquals(false, j.queryForObject("SELECT stock_applied FROM mes_material_issue_input WHERE document_id=?", Boolean.class, document));
            m.perform(full(post("/api/production/input")).content(confirmedCommand)).andExpect(status().isOk())
                    .andExpect(jsonPath("stockApplied").value(false));
        }
    }
}
