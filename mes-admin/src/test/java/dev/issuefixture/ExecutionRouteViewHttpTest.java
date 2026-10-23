package dev.issuefixture;

import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * TEST ONLY: exact execution version metadata and graph, including unselected paths.
 */
class ExecutionRouteViewHttpTest {
    @Test
    void graphUsesExecutionVersionEvenBeforeSelection() throws Exception {
        try (var context = ProductionReportHttpTest.context()) {
            var jdbc = context.getBean(MaterialIssueHttpTest.Fixture.class).business.get("A");
            jdbc.update("INSERT INTO routing_node(id,routing_id,node_code,operation_id,operation_code,operation_name,display_order,is_key) VALUES(21,2,'V2-A',1,'O1','第二版节点',0,false)");
            jdbc.update("INSERT INTO mes_production_execution(id,workorder_id,product_id,routing_id,target_qty,status) VALUES(3,1,1,2,10,'READY')");
            var mvc = MaterialIssueHttpTest.mvc(context);
            mvc.perform(ProductionReportHttpTest.full(get("/api/production/executions/1/path"), "A"))
                    .andExpect(status().isOk()).andExpect(jsonPath("executionId").value("1"))
                    .andExpect(jsonPath("routingId").value("1"))
                    .andExpect(jsonPath("routingCode").value("R1"))
                    .andExpect(jsonPath("routingVersion").value(1))
                    .andExpect(jsonPath("pathStatus").value("LOCKED"))
                    .andExpect(jsonPath("selectedNodeIds[0]").value("11"))
                    .andExpect(jsonPath("nodes[0].operationId").value("1"));
            mvc.perform(ProductionReportHttpTest.full(get("/api/production/executions/3/path"), "A"))
                    .andExpect(status().isOk()).andExpect(jsonPath("executionId").value("3"))
                    .andExpect(jsonPath("routingId").value("2"))
                    .andExpect(jsonPath("routingVersion").value(2))
                    .andExpect(jsonPath("pathStatus").value("UNSELECTED"))
                    .andExpect(jsonPath("selectedNodeIds.length()").value(0))
                    .andExpect(jsonPath("nodes.length()").value(1))
                    .andExpect(jsonPath("nodes[0].id").value("21"))
                    .andExpect(jsonPath("edges.length()").value(0));
        }
    }
}
