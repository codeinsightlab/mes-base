package dev.issuefixture;

import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * TEST ONLY: real Mapper SQL in isolated H2 databases, not deployed MySQL evidence.
 */
class WorkOrderApplicableRoutingHttpTest {
    @Test
    void onlyPublishedRoutesMatchedToSelectedProductAreReturned() throws Exception {
        try (var context = MaterialIssueHttpTest.context()) {
            var mvc = MaterialIssueHttpTest.mvc(context);
            var jdbc = context.getBean(MaterialIssueHttpTest.Fixture.class).business.get("A");
            jdbc.update("INSERT INTO material(id,code,name,unit_id,status) VALUES(2,'M2','另一产品',1,'ENABLED')");
            jdbc.update("INSERT INTO routing(id,code,name,version,status,revision,published_at) VALUES(3,'R2','另一产品工艺',1,'PUBLISHED',0,CURRENT_TIMESTAMP)");
            jdbc.update("INSERT INTO routing_product(routing_id,material_id) VALUES(3,2),(2,1)");
            String url = "/api/production/workorder/references/routings";
            mvc.perform(get(url).header("Authorization", "Bearer FULL").header("X-Factory-Id", "A")
                            .param("productId", "1"))
                    .andExpect(status().isOk()).andExpect(jsonPath("length()").value(1))
                    .andExpect(jsonPath("[0].id").value("1"));
            mvc.perform(get(url).header("Authorization", "Bearer FULL").header("X-Factory-Id", "A")
                            .param("productId", "2"))
                    .andExpect(status().isOk()).andExpect(jsonPath("length()").value(1))
                    .andExpect(jsonPath("[0].id").value("3"));
            mvc.perform(get(url).header("Authorization", "Bearer FULL").header("X-Factory-Id", "A")
                            .param("productId", "1").param("search", "另一产品"))
                    .andExpect(status().isOk()).andExpect(jsonPath("length()").value(0));
            mvc.perform(get(url).header("Authorization", "Bearer FULL").header("X-Factory-Id", "B")
                            .param("productId", "2"))
                    .andExpect(status().isOk()).andExpect(jsonPath("length()").value(0));
            mvc.perform(get(url).header("Authorization", "Bearer NONE").header("X-Factory-Id", "A")
                            .param("productId", "1"))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    void itemOutputCanCreateEditReleaseAndCreateExecutionWhileReferenceChecksRemain() throws Exception {
        try (var context = MaterialIssueHttpTest.context()) {
            var mvc = MaterialIssueHttpTest.mvc(context);
            var jdbc = context.getBean(MaterialIssueHttpTest.Fixture.class).business.get("A");
            jdbc.update("INSERT INTO material(id,code,name,unit_id,status) VALUES(2,'STAGE2','阶段产出物料',1,'ENABLED')");
            jdbc.update("INSERT INTO routing_product(routing_id,material_id) VALUES(1,2)");
            String base = "/api/production/workorder";
            mvc.perform(get(base + "/references/routings").header("Authorization", "Bearer FULL")
                            .header("X-Factory-Id", "A").param("productId", "2"))
                    .andExpect(status().isOk()).andExpect(jsonPath("[0].id").value("1"));
            String body = "{\"code\":\"ITEM_OUTPUT\",\"productId\":\"2\",\"routingId\":\"1\",\"plannedQty\":\"10\"}";
            mvc.perform(post(base).header("Authorization", "Bearer FULL").header("X-Factory-Id", "A")
                            .contentType("application/json").content(body))
                    .andExpect(status().isOk()).andExpect(jsonPath("workorder.productId").value("2"));
            Long id = jdbc.queryForObject("SELECT id FROM workorder WHERE code='ITEM_OUTPUT'", Long.class);
            mvc.perform(put(base + "/" + id).header("Authorization", "Bearer FULL").header("X-Factory-Id", "A")
                            .contentType("application/json")
                            .content(body.replace("\"plannedQty\"", "\"revision\":0,\"plannedQty\"")))
                    .andExpect(status().isOk());
            mvc.perform(post(base + "/" + id + "/actions/release").header("Authorization", "Bearer FULL")
                            .header("X-Factory-Id", "A").contentType("application/json").content("{\"revision\":1}"))
                    .andExpect(status().isOk()).andExpect(jsonPath("workorder.status").value("RELEASED"));
            mvc.perform(post(ProductionExecutionHttpTest.E).header("Authorization", "Bearer FULL")
                            .header("X-Factory-Id", "A").contentType("application/json")
                            .content(ProductionExecutionHttpTest.CREATE.replace("12.12", "10")
                                    .replace("\"workorderId\":\"1\"", "\"workorderId\":\"" + id + "\"")))
                    .andExpect(status().isOk()).andExpect(jsonPath("productId").value("2"));
            // Existing material existence/enabled, published version and applicability checks still apply.
            for (String rejected : new String[]{
                    body.replace("\"routingId\":\"1\"", "\"routingId\":\"2\""),
                    body.replace("\"productId\":\"2\"", "\"productId\":\"999\"")})
                mvc.perform(post(base).header("Authorization", "Bearer FULL").header("X-Factory-Id", "A")
                                .contentType("application/json").content(rejected))
                        .andExpect(status().is4xxClientError());
            jdbc.update("DELETE FROM routing_product WHERE routing_id=1 AND material_id=2");
            mvc.perform(post(base).header("Authorization", "Bearer FULL").header("X-Factory-Id", "A")
                            .contentType("application/json").content(body))
                    .andExpect(status().is4xxClientError());
            jdbc.update("INSERT INTO routing_product(routing_id,material_id) VALUES(1,2)");
            jdbc.update("UPDATE material SET status='DISABLED' WHERE id=2");
            mvc.perform(post(base).header("Authorization", "Bearer FULL").header("X-Factory-Id", "A")
                            .contentType("application/json").content(body))
                    .andExpect(status().is4xxClientError());
        }
    }

}
