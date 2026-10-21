package dev.issuefixture;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * TEST ONLY: real generated XML, fixed transactions and authorization across two separate H2 databases.
 */
class ProductionExecutionHttpTest {
    static final String E = "/api/production/mes-production-execution";
    static final String QR = "/api/production/identity";
    static final String CREATE = "{\"workorderId\":\"1\",\"routingId\":\"1\",\"targetQty\":\"12.12\",\"status\":\"READY\"}";

    private MockHttpServletRequestBuilder full(MockHttpServletRequestBuilder request, String factory) {
        return request.header("Authorization", "Bearer FULL").header("X-Factory-Id", factory)
                .contentType("application/json");
    }

    @Test
    void independentIdentitiesRoutesStableNumbersAndReadOnlyScan() throws Exception {
        try (var c = MaterialIssueHttpTest.context()) {
            var m = MaterialIssueHttpTest.mvc(c);
            var fixture = c.getBean(MaterialIssueHttpTest.Fixture.class);
            var a = fixture.business.get("A");
            a.update("INSERT INTO routing(id,code,name,version,status,revision,published_at) VALUES(3,'R1','另一个发布版本',3,'PUBLISHED',0,CURRENT_TIMESTAMP)");
            a.update("INSERT INTO routing_product(routing_id,material_id,material_code,material_name) VALUES(3,1,'M1','产品')");
            for (String routing : List.of("1", "3", "1")) {
                m.perform(full(post(E), "A").content(CREATE.replace("\"routingId\":\"1\"", "\"routingId\":\"" + routing + "\"")))
                        .andExpect(status().isOk()).andExpect(jsonPath("productId").value("1"))
                        .andExpect(jsonPath("routingId").value(routing)).andExpect(jsonPath("createBy").value("1"));
            }
            assertEquals(3, a.queryForObject("SELECT COUNT(DISTINCT execution_no) FROM mes_production_execution", Integer.class));
            m.perform(full(get(E + "/1"), "A")).andExpect(status().isOk())
                    .andExpect(jsonPath("executionNo").value("EXE-1"));
            m.perform(full(put(E + "/1"), "A").content(CREATE.replace("12.12", "8.01")))
                    .andExpect(status().isOk()).andExpect(jsonPath("executionNo").value("EXE-1"));
            // Route correction is permitted before any execution fact; each execution remains independent.
            m.perform(full(put(E + "/2"), "A").content(CREATE)).andExpect(status().isOk())
                    .andExpect(jsonPath("routingId").value("1"));
            m.perform(full(put(E + "/2"), "A").content(CREATE.replace("\"routingId\":\"1\"", "\"routingId\":\"3\"")))
                    .andExpect(status().isOk());
            a.update("UPDATE routing SET status='DISABLED' WHERE id=3");
            m.perform(full(put(E + "/2"), "A").content(CREATE.replace("\"routingId\":\"1\"", "\"routingId\":\"3\"")
                            .replace("READY", "RUNNING")
                            .replace("\"status\"", "\"startTime\":\"2026-10-14T09:00:00\",\"status\"")))
                    .andExpect(status().isConflict());
            var before = a.queryForList("SELECT * FROM mes_production_execution ORDER BY id");
            m.perform(full(get(QR).param("code", "EXE:1"), "A"))
                    .andExpect(status().isOk()).andExpect(jsonPath("executionId").value("1"))
                    .andExpect(jsonPath("workorderCode").value("WO1"))
                    .andExpect(jsonPath("productCode").value("M1"))
                    .andExpect(jsonPath("routingVersion").value(1)).andExpect(jsonPath("status").value("READY"));
            assertEquals(before, a.queryForList("SELECT * FROM mes_production_execution ORDER BY id"));
            assertEquals(0, a.queryForObject("SELECT COUNT(*) FROM mes_material_issue", Integer.class));
            m.perform(full(get(QR).param("code", "EXE:1"), "B")).andExpect(status().isNotFound());
            m.perform(full(get(QR).param("code", "EXE:99999"), "A")).andExpect(status().isNotFound())
                    .andExpect(jsonPath("errorCode").value("EXECUTION_NOT_FOUND"));
            for (String code : List.of("exe:1", "EXE:0", "EXE:-1", "EXE:01", "ERP:M1", "EXE:9223372036854775808", "EXE:1 "))
                m.perform(full(get(QR).param("code", code), "A")).andExpect(status().isBadRequest())
                        .andExpect(jsonPath("errorCode").value("EXECUTION_QR_INVALID"));
            m.perform(full(post(E), "B").content(CREATE)).andExpect(status().isOk())
                    .andExpect(jsonPath("id").value("1"));
            m.perform(full(get(QR).param("code", "EXE:1"), "B")).andExpect(status().isOk());
            assertEquals(3, a.queryForObject("SELECT COUNT(*) FROM mes_production_execution", Integer.class));
        }
    }

    @Test
    void issueAuthorityLegacyCompatibilityAndFrozenFacts() throws Exception {
        try (var c = MaterialIssueHttpTest.context()) {
            var m = MaterialIssueHttpTest.mvc(c);
            var a = c.getBean(MaterialIssueHttpTest.Fixture.class).business.get("A");
            a.update("INSERT INTO routing(id,code,name,version,status,revision,published_at) VALUES(3,'R1','发布三',3,'PUBLISHED',0,CURRENT_TIMESTAMP)");
            a.update("INSERT INTO routing_product(routing_id,material_id,material_code,material_name) VALUES(3,1,'M1','产品')");
            String alternate = CREATE.replace("\"routingId\":\"1\"", "\"routingId\":\"3\"");
            m.perform(full(post(E), "A").content(alternate)).andExpect(status().isOk());
            String issue = "{\"issueNo\":\"QA_EXECUTION_ISSUE\",\"executionId\":\"1\",\"status\":\"RECORDED\"}";
            for (int i = 0; i < 2; i++)
                m.perform(full(post(MaterialIssueHttpTest.H), "A").content(issue))
                        .andExpect(status().isOk()).andExpect(jsonPath("executionId").value("1"))
                        .andExpect(jsonPath("workorderId").value("1")).andExpect(jsonPath("routingId").value("3"));
            m.perform(full(post(MaterialIssueHttpTest.H), "A").content(MaterialIssueHttpTest.HEADER))
                    .andExpect(status().isOk()).andExpect(jsonPath("executionId").isEmpty())
                    .andExpect(jsonPath("routingId").value("1"));
            m.perform(full(post(MaterialIssueHttpTest.H), "A").content(issue.replace("\"status\"", "\"routingId\":\"1\",\"status\"")))
                    .andExpect(status().isBadRequest());
            m.perform(full(post(MaterialIssueHttpTest.H), "A").content(issue.replace("\"status\"", "\"workorderId\":\"999\",\"status\"")))
                    .andExpect(status().isBadRequest());
            m.perform(full(post(MaterialIssueHttpTest.H), "B").content(issue)).andExpect(status().isNotFound());
            m.perform(full(put(E + "/1"), "A").content(CREATE)).andExpect(status().isConflict());
            m.perform(full(put(E + "/1"), "A").content(alternate.replace("12.12", "15.01")))
                    .andExpect(status().isOk());
            m.perform(full(delete(E), "A").content("[1]")).andExpect(status().isConflict())
                    .andExpect(jsonPath("errorCode").value("EXECUTION_REFERENCED"));
            m.perform(full(put(MaterialIssueHttpTest.H + "/1"), "A").content(MaterialIssueHttpTest.HEADER))
                    .andExpect(status().isConflict());
            m.perform(full(delete(MaterialIssueHttpTest.H), "A").content("[1]")).andExpect(status().isConflict());
            assertEquals(2, a.queryForObject("SELECT COUNT(*) FROM mes_material_issue WHERE execution_id=1", Integer.class));
        }
    }

    @Test
    void permissionsReferenceNegativesAndStartedProtection() throws Exception {
        try (var c = MaterialIssueHttpTest.context()) {
            var m = MaterialIssueHttpTest.mvc(c);
            var a = c.getBean(MaterialIssueHttpTest.Fixture.class).business.get("A");
            a.update("INSERT INTO routing(id,code,name,version,status,revision,published_at) VALUES(4,'R_OTHER','不适用产品',1,'PUBLISHED',0,CURRENT_TIMESTAMP)");
            a.update("INSERT INTO workorder(id,code,product_id,planned_qty,routing_id,revision,create_time,update_time) VALUES(9,'WO_A_ONLY',1,1000,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");
            m.perform(full(post(E), "B").content(CREATE.replace("\"workorderId\":\"1\"", "\"workorderId\":\"9\"")))
                    .andExpect(status().isUnprocessableEntity());
            m.perform(get(QR).header("Authorization", "Bearer NONE").header("X-Factory-Id", "A").param("code", "EXE:1"))
                    .andExpect(status().isForbidden());
            for (String body : List.of(CREATE.replace("\"routingId\":\"1\"", "\"routingId\":\"4\""), CREATE.replace("\"routingId\":\"1\"", "\"routingId\":\"2\""),
                    CREATE.replace("\"workorderId\":\"1\"", "\"workorderId\":\"999\""),
                    CREATE.replace("12.12", "0"), CREATE.replace("12.12", "1.1234567")))
                m.perform(full(post(E), "A").content(body)).andExpect(status().is4xxClientError());
            m.perform(post(E).header("Authorization", "Bearer CRUD").header("X-Factory-Id", "A")
                    .contentType("application/json").content(CREATE)).andExpect(status().isForbidden());
            m.perform(full(post(E), "A").content(CREATE)).andExpect(status().isOk());
            m.perform(put(E + "/1").header("Authorization", "Bearer CRUD").header("X-Factory-Id", "A")
                    .contentType("application/json").content(CREATE)).andExpect(status().isForbidden());
            m.perform(full(post("/api/production/workorder/1/actions/release"), "A").content("{\"revision\":0}"))
                    .andExpect(status().isOk());
            m.perform(full(put(E + "/1"), "A").content(CREATE.replace("READY", "RUNNING")
                            .replace("\"status\"", "\"startTime\":\"2026-10-14T09:00:00\",\"status\"")))
                    .andExpect(status().isConflict());
            // Existing started records remain protected; formal start coverage is in ProductionCoreV1HttpTest.
            c.getBean(MaterialIssueHttpTest.Fixture.class).business.get("A")
                    .update("UPDATE mes_production_execution SET status='RUNNING',start_time=CURRENT_TIMESTAMP WHERE id=1");
            m.perform(full(put(E + "/1"), "A").content(CREATE)).andExpect(status().isConflict());
            m.perform(full(delete(E), "A").content("[1]")).andExpect(status().isConflict());
            m.perform(delete(E).header("Authorization", "Bearer CRUD").header("X-Factory-Id", "A")
                    .contentType("application/json").content("[1]")).andExpect(status().isForbidden());
            m.perform(get(QR).param("code", "EXE:1")).andExpect(status().isForbidden());
            m.perform(get(QR).header("Authorization", "Bearer FULL").param("code", "EXE:1"))
                    .andExpect(status().isUnprocessableEntity());
            m.perform(full(post(E), "A").content(CREATE)).andExpect(status().isOk());
            m.perform(full(delete(E), "A").content("[2,999]")).andExpect(status().isNotFound());
            m.perform(full(get(E + "/2"), "A")).andExpect(status().isOk());
            m.perform(full(delete(E), "A").content("[2]")).andExpect(status().isNoContent());
        }
    }
}
