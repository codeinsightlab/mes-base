package dev.issuefixture;

import java.math.BigDecimal;
import dev.mesbase.business.production.controller.ProductionReportController;
import dev.mesbase.business.production.service.ProductionReportService;
import dev.mesbase.business.production.service.ProductionQuantityService;
import dev.mesbase.business.production.service.ReportUserReferenceService;
import dev.mesbase.business.basic.dao.WorkstationMapper;
import dev.mesbase.core.error.ErrorCode;
import dev.mesbase.core.error.FoundationException;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.MapPropertySource;
import org.springframework.mock.web.MockServletContext;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import com.fasterxml.jackson.databind.ObjectMapper;

import static org.mockito.ArgumentMatchers.any;

import org.springframework.jdbc.core.JdbcTemplate;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.UUID;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.CyclicBarrier;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

/**
 * TEST ONLY real generated SQL, Spring transactions, permissions and two routed H2 DBs. Center identity is mocked.
 */
class ProductionReportHttpTest {
    static final String ROOT = "/api/production/reports";

    @Configuration
    @Import({ProductionReportController.class, ProductionReportService.class, ProductionQuantityService.class})
    static class Extra {
        @Bean
        WorkstationMapper workstations(SqlSessionFactory f) {
            return new SqlSessionTemplate(f).getMapper(WorkstationMapper.class);
        }

        @Bean
        ReportUserReferenceService users() {
            var service = mock(ReportUserReferenceService.class);
            doAnswer(i -> {
                if (!Long.valueOf(100).equals(i.getArgument(0)))
                    throw new FoundationException(ErrorCode.REPORT_REFERENCE_INVALID);
                return null;
            }).when(service).requireExists(any());
            return service;
        }
    }

    static AnnotationConfigWebApplicationContext context() throws Exception {
        var c = new AnnotationConfigWebApplicationContext();
        c.setServletContext(new MockServletContext());
        c.getEnvironment().getPropertySources()
                .addFirst(new MapPropertySource("TEST ONLY", Map.of("mes.business.enabled", "true", "mes.integration.warehouse.factories.A.mode", "LOCAL_ONLY", "mes.integration.warehouse.factories.B.mode", "LOCAL_ONLY")));
        c.register(MaterialIssueHttpTest.Web.class, Extra.class);
        c.refresh();
        for (var jdbc : c.getBean(MaterialIssueHttpTest.Fixture.class).business.values()) {
            seed(jdbc);
            ProductionInputTestData.confirmed(jdbc, 1, new BigDecimal("10"));
            ProductionInputTestData.confirmed(jdbc, 2, new BigDecimal("10"));
        }
        return c;
    }

    static void seed(JdbcTemplate jdbc) {
        jdbc.update("UPDATE workorder SET status='RELEASED' WHERE id=1");
        jdbc.update("INSERT INTO operation(id,code,name,status,sort,revision) VALUES(1,'O1','钻孔','ENABLED',0,0)");
        jdbc.update("INSERT INTO routing_node(id,routing_id,node_code,operation_id,operation_code,operation_name,display_order,is_key) VALUES(11,1,'A',1,'O1','首工序',99,false),(12,1,'B',1,'O1','中工序',1,false),(13,1,'C',1,'O1','末工序',0,false)");
        jdbc.update("INSERT INTO routing_edge(routing_id,from_node_id,to_node_id) VALUES(1,11,12),(1,12,13)");
        jdbc.update("INSERT INTO mes_production_execution(id,execution_no,workorder_id,product_id,routing_id,target_qty,status) VALUES(1,'EXE-1',1,1,1,10,'READY'),(2,'EXE-2',1,1,1,10,'READY')");
        jdbc.update("INSERT INTO mes_execution_path(id,routing_id,path_status,revision,selected_at,locked_at) VALUES(1,1,'LOCKED',1,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP),(2,1,'LOCKED',1,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");
        jdbc.update("INSERT INTO mes_execution_path_node(execution_id,sequence_no,routing_node_id) VALUES(1,1,11),(1,2,12),(1,3,13),(2,1,11),(2,2,12),(2,3,13)");
    }

    static MockHttpServletRequestBuilder full(MockHttpServletRequestBuilder request, String factory) {
        return request.header("Authorization", "Bearer FULL").header("X-Factory-Id", factory)
                .contentType("application/json");
    }

    static String supplement(long node, String quantity) {
        return "{\"requestId\":\"" + UUID.randomUUID() + "\",\"executionId\":\"1\",\"routingNodeId\":\"" + node + "\",\"quantity\":\"" + quantity + "\",\"operatorUserId\":\"100\",\"reportTime\":\"2026-01-01T10:00:00\",\"reason\":\"TEST ONLY历史补录\",\"recordedBy\":999}";
    }

    static long add(MockMvc m, long node, String quantity) throws Exception {
        String body = m.perform(full(post(ROOT + "/supplements"), "A").content(supplement(node, quantity)))
                .andExpect(status().isOk()).andExpect(jsonPath("recordedBy").value("1")).andReturn().getResponse()
                .getContentAsString();
        return Long.parseLong(new ObjectMapper().readTree(body).get("id").asText());
    }

    @Test
    void sumsExactQuantitiesValidatesUpstreamAndPreservesQualityMeaning() throws Exception {
        try (var c = context()) {
            var m = MaterialIssueHttpTest.mvc(c);
            long id = add(m, 11, "5.000001");
            add(m, 11, "2");
            m.perform(full(get("/api/production/report-quantities/executions/1"), "A")).andExpect(status().isOk())
                    .andExpect(jsonPath("nodes[0].routingNodeId").value("11"))
                    .andExpect(jsonPath("nodes[0].reportedQty").value("7.000001"))
                    .andExpect(jsonPath("nodes[0].qualityIntegrated").value(true))
                    .andExpect(jsonPath("nodes[1].availableQty").value("7.000001"));
            m.perform(full(post(ROOT + "/supplements"), "A").content(supplement(12, "8")))
                    .andExpect(status().isConflict());
            m.perform(full(post(ROOT + "/supplements"), "A").content(supplement(13, "1")))
                    .andExpect(status().isConflict());
            add(m, 12, "6");
            m.perform(full(post(ROOT + "/" + id + "/adjustments"), "A").content("{\"requestId\":\"" + UUID.randomUUID() + "\",\"quantityDelta\":\"-2\",\"reason\":\"倒挂拒绝\"}"))
                    .andExpect(status().isConflict());
            m.perform(full(get("/api/production/report-quantities/executions/1"), "B"))
                    .andExpect(jsonPath("nodes[0].reportedQty").value("0"));
            m.perform(full(get("/api/production/report-quantities/executions/2"), "A"))
                    .andExpect(jsonPath("nodes[0].reportedQty").value("0"));
            m.perform(full(get("/api/production/report-quantities/workorders/1"), "A")).andExpect(status().isOk())
                    .andExpect(jsonPath("total").value(2));
            m.perform(full(put("/api/production/mes-production-execution/1"), "A").content("{\"workorderId\":1,\"routingId\":1,\"targetQty\":6,\"status\":\"READY\"}"))
                    .andExpect(status().isOk());
        }
    }

    @Test
    void adjustmentsAndVoidAreAppendOnlyAndKeepOriginalIdentity() throws Exception {
        try (var c = context()) {
            var m = MaterialIssueHttpTest.mvc(c);
            var jdbc = c.getBean(MaterialIssueHttpTest.Fixture.class).business.get("A");
            long id = add(m, 11, "4");
            m.perform(full(post(ROOT + "/" + id + "/adjustments"), "A").content("{\"requestId\":\"" + UUID.randomUUID() + "\",\"quantityDelta\":\"2\",\"reason\":\"增加\"}"))
                    .andExpect(status().isOk()).andExpect(jsonPath("operatorUserId").value("100"));
            m.perform(full(post(ROOT + "/" + id + "/adjustments"), "A").content("{\"requestId\":\"" + UUID.randomUUID() + "\",\"quantityDelta\":\"-1\",\"reason\":\"减少\"}"))
                    .andExpect(status().isOk());
            long adjustment = jdbc.queryForObject("SELECT MAX(id) FROM mes_production_report", Long.class);
            m.perform(full(post(ROOT + "/" + adjustment + "/adjustments"), "A").content("{\"requestId\":\"" + UUID.randomUUID() + "\",\"quantityDelta\":\"1\",\"reason\":\"错误引用\"}"))
                    .andExpect(status().isUnprocessableEntity());
            m.perform(full(post(ROOT + "/" + id + "/void"), "A").content("{\"requestId\":\"" + UUID.randomUUID() + "\",\"reason\":\"整笔冲销\"}"))
                    .andExpect(status().isOk()).andExpect(jsonPath("quantityDelta").value("-5.000000"));
            m.perform(full(post(ROOT + "/" + id + "/void"), "A").content("{\"requestId\":\"" + UUID.randomUUID() + "\",\"reason\":\"重复冲销\"}"))
                    .andExpect(status().isConflict());
            m.perform(full(get(ROOT + "/" + id), "A")).andExpect(jsonPath("originalEffectiveQty").value("0.000000"));
            m.perform(full(get(ROOT + "/" + id + "/adjustments"), "A")).andExpect(jsonPath("total").value(3));
            assertEquals(4, jdbc.queryForObject("SELECT quantity_delta FROM mes_production_report WHERE id=?", Integer.class, id));
            assertEquals(4, jdbc.queryForObject("SELECT COUNT(*) FROM mes_production_report", Integer.class));
            m.perform(full(put(ROOT + "/" + id), "A").content("{}")).andExpect(status().isMethodNotAllowed());
            m.perform(full(delete(ROOT + "/" + id), "A")).andExpect(status().isMethodNotAllowed());
        }
    }

    @Test
    void rejectsInvalidReasonsReferencesUnselectedPathsAndUnauthorizedWrites() throws Exception {
        try (var c = context()) {
            var m = MaterialIssueHttpTest.mvc(c);
            var jdbc = c.getBean(MaterialIssueHttpTest.Fixture.class).business.get("A");
            m.perform(full(post(ROOT + "/supplements"), "A").content(supplement(11, "0")))
                    .andExpect(status().isBadRequest());
            m.perform(full(post(ROOT + "/supplements"), "A").content(supplement(11, "1").replace("100", "999")))
                    .andExpect(status().isUnprocessableEntity());
            m.perform(full(post(ROOT + "/supplements"), "A").content(supplement(11, "1").replace("TEST ONLY历史补录", " ")))
                    .andExpect(status().isBadRequest());
            m.perform(post(ROOT + "/supplements").header("Authorization", "Bearer CRUD").header("X-Factory-Id", "A")
                    .contentType("application/json").content(supplement(11, "1"))).andExpect(status().isForbidden());
            m.perform(post(ROOT + "/supplements").contentType("application/json").content(supplement(11, "1")))
                    .andExpect(status().isForbidden());
            jdbc.update("UPDATE mes_execution_path SET path_status='SELECTED' WHERE id=1");
            m.perform(full(post(ROOT + "/supplements"), "A").content(supplement(11, "1")))
                    .andExpect(status().isConflict());
            jdbc.update("DELETE FROM mes_execution_path WHERE id=1");
            m.perform(full(get("/api/production/report-quantities/executions/1"), "A")).andExpect(status().isOk())
                    .andExpect(jsonPath("writable").value(false)).andExpect(jsonPath("nodes").isEmpty());
            assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM mes_production_report", Integer.class));
        }
    }

    @Test
    void fixedSelectedPathRejectsAnUnchosenBranchAndFactoryIdCollisionsStayLocal() throws Exception {
        try (var c = context()) {
            var m = MaterialIssueHttpTest.mvc(c);
            var jdbc = c.getBean(MaterialIssueHttpTest.Fixture.class).business.get("A");
            jdbc.update("INSERT INTO routing_node(id,routing_id,node_code,operation_id,operation_code,operation_name,display_order,is_key) VALUES(14,1,'ALT',1,'O1','未选分支',-1,false)");
            jdbc.update("INSERT INTO routing_edge(routing_id,from_node_id,to_node_id) VALUES(1,11,14),(1,14,13)");
            m.perform(full(post(ROOT + "/supplements"), "A").content(supplement(14, "1")))
                    .andExpect(status().isConflict());
            long original = add(m, 11, "4");
            m.perform(full(post(ROOT + "/supplements"), "B").content(supplement(11, "2"))).andExpect(status().isOk())
                    .andExpect(jsonPath("id").value(String.valueOf(original)));
            m.perform(full(get(ROOT + "/" + original), "A"))
                    .andExpect(jsonPath("record.quantityDelta").value("4.000000"));
            m.perform(full(get(ROOT + "/" + original), "B"))
                    .andExpect(jsonPath("record.quantityDelta").value("2.000000"));
            m.perform(full(get("/api/production/report-quantities/executions/1"), "A"))
                    .andExpect(jsonPath("nodes.length()").value(3)).andExpect(jsonPath("nodes[0].code").value("A"));
        }
    }

    @Test
    void administratorCommandsReplayLostResponsesAndRejectChangedContent() throws Exception {
        try (var c = context(); var executor = Executors.newFixedThreadPool(2)) {
            var m = MaterialIssueHttpTest.mvc(c);
            String body = supplement(11, "4");
            var barrier = new CyclicBarrier(2);
            Callable<String> submit = () -> {
                barrier.await();
                return m.perform(full(post(ROOT + "/supplements"), "A").content(body))
                        .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            };
            var a = executor.submit(submit);
            var b = executor.submit(submit);
            String first = a.get();
            assertEquals(first, b.get());
            String id = new ObjectMapper().readTree(first).get("id").asText();
            m.perform(full(post(ROOT + "/supplements"), "A").content(body))
                    .andExpect(status().isOk()).andExpect(jsonPath("id").value(id));
            m.perform(full(post(ROOT + "/supplements"), "A").content(body.replace("历史补录", "不同命令")))
                    .andExpect(status().isConflict()).andExpect(jsonPath("errorCode").value("REPORT_REQUEST_CONFLICT"));
            String adjustment = "{\"requestId\":\"" + UUID.randomUUID() + "\",\"quantityDelta\":\"-1\",\"reason\":\"TEST ONLY correction\"}";
            String endpoint = ROOT + "/" + id + "/adjustments";
            String adjusted = m.perform(full(post(endpoint), "A").content(adjustment)).andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();
            assertEquals(adjusted, m.perform(full(post(endpoint), "A").content(adjustment)).andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString());
            String cancel = "{\"requestId\":\"" + UUID.randomUUID() + "\",\"reason\":\"TEST ONLY void\"}";
            String voided = m.perform(full(post(ROOT + "/" + id + "/void"), "A").content(cancel))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            assertEquals(voided, m.perform(full(post(ROOT + "/" + id + "/void"), "A").content(cancel))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
            m.perform(full(post(ROOT + "/" + id + "/adjustments"), "A").content(cancel.replace("}", ",\"quantityDelta\":\"-3\"}")))
                    .andExpect(status().isConflict());
            m.perform(full(post(ROOT + "/" + id + "/void"), "A").content("{\"reason\":\"legacy\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("errorCode").value("REPORT_REQUEST_ID_REQUIRED"));
            var j = c.getBean(MaterialIssueHttpTest.Fixture.class).business.get("A");
            assertEquals(3, j.queryForObject("SELECT COUNT(*) FROM mes_production_report", Integer.class));
            assertEquals(0, j.queryForObject("SELECT SUM(quantity_delta) FROM mes_production_report", Integer.class));
        }
    }

    @Test
    void concurrentSupplementsSerializeOnExecutionAndCannotOverReport() throws Exception {
        try (var c = context(); var executor = Executors.newFixedThreadPool(2)) {
            var m = MaterialIssueHttpTest.mvc(c);
            add(m, 11, "8");
            var barrier = new CyclicBarrier(2);
            Callable<Integer> submit = () -> {
                barrier.await();
                return m.perform(full(post(ROOT + "/supplements"), "A").content(supplement(11, "2"))).andReturn()
                        .getResponse().getStatus();
            };
            var a = executor.submit(submit);
            var b = executor.submit(submit);
            var results = List.of(a.get(), b.get());
            assertTrue(results.contains(200));
            assertTrue(results.contains(409));
            assertEquals(10, c.getBean(MaterialIssueHttpTest.Fixture.class).business.get("A")
                    .queryForObject("SELECT SUM(quantity_delta) FROM mes_production_report", Integer.class));
        }
    }
}
