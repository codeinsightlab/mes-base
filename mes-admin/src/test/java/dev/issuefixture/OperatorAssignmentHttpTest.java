package dev.issuefixture;

import dev.mesbase.business.production.controller.MesOperationUserController;
import dev.mesbase.business.production.controller.MesExecutionAssignmentController;
import dev.mesbase.business.production.controller.OperatorDirectoryController;
import dev.mesbase.business.production.dao.MesOperationUserMapper;
import dev.mesbase.business.production.dao.MesExecutionAssignmentMapper;
import dev.mesbase.business.production.service.OperationUserDirectory;
import dev.mesbase.business.production.service.OperatorQualificationRules;
import dev.mesbase.business.production.service.OperatorCandidateService;
import dev.mesbase.business.production.service.ReportOperatorEligibilityService;
import dev.mesbase.business.production.service.impl.MesOperationUserServiceImpl;
import dev.mesbase.business.production.service.impl.MesExecutionAssignmentServiceImpl;
import dev.mesbase.core.error.ErrorCode;
import dev.mesbase.core.error.FoundationException;
import dev.mesbase.system.production.OperationUserDirectoryMapper.UserOption;
import dev.mesbase.platform.execution.FactoryContext;
import dev.mesbase.core.factory.FactoryId;
import dev.mesbase.core.factory.FactoryScope;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.MapPropertySource;
import org.springframework.mock.web.MockServletContext;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static dev.issuefixture.ProductionReportHttpTest.full;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

/**
 * TEST ONLY two isolated H2 DBs, real request authorization/transactions/SQL, mocked center identity directory.
 */
class OperatorAssignmentHttpTest {
    @Configuration
    @Import({MesOperationUserServiceImpl.class, MesExecutionAssignmentServiceImpl.class, MesOperationUserController.class,
            MesExecutionAssignmentController.class, OperatorQualificationRules.class, ReportOperatorEligibilityService.class,
            OperatorCandidateService.class, OperatorDirectoryController.class})
    static class Extra {
        @Bean
        MesOperationUserMapper capabilities(SqlSessionFactory sessions) {
            sessions.getConfiguration().addMapper(MesOperationUserMapper.class);
            return new SqlSessionTemplate(sessions).getMapper(MesOperationUserMapper.class);
        }

        @Bean
        OperationUserDirectory directory() {
            var d = mock(OperationUserDirectory.class);
            doAnswer(i -> {
                Long id = i.getArgument(0);
                if (id != 1L && id != 2L) throw new FoundationException(ErrorCode.OPERATOR_NOT_AUTHORIZED);
                return null;
            }).when(d).requireValid(any());
            when(d.validUsers(any())).thenAnswer(i -> {
                List<Long> ids = i.getArgument(0);
                return ids.stream().filter(id -> id == 1L || id == 2L).map(id -> new UserOption(id, "TEST_USER_" + id))
                        .toList();
            });
            return d;
        }
    }

    static AnnotationConfigWebApplicationContext context() throws Exception {
        var c = new AnnotationConfigWebApplicationContext();
        c.setServletContext(new MockServletContext());
        c.getEnvironment().getPropertySources()
                .addFirst(new MapPropertySource("TEST ONLY", Map.of("mes.business.enabled", "true")));
        c.register(MaterialIssueHttpTest.Web.class, Extra.class);
        c.refresh();
        for (var jdbc : c.getBean(MaterialIssueHttpTest.Fixture.class).business.values()) {
            jdbc.update("INSERT INTO operation(id,code,name,status,sort,revision) VALUES(1,'DRILL','钻孔','ENABLED',0,0),(2,'TURN','车削','ENABLED',0,0)");
            jdbc.update("INSERT INTO routing_node(id,routing_id,node_code,operation_id,operation_code,operation_name,is_key,display_order) VALUES(1,1,'A',1,'DRILL','钻孔',TRUE,0),(2,1,'B',1,'DRILL','钻孔',TRUE,1),(3,2,'WRONG',1,'DRILL','钻孔',TRUE,0)");
            jdbc.update("INSERT INTO mes_production_execution(id,workorder_id,product_id,routing_id,target_qty,status) VALUES(1,1,1,1,100,'READY'),(2,1,1,1,100,'FULLY_RECEIVED')");
        }
        return c;
    }

    static final String C = "/api/production/mes-operation-user", A = "/api/production/mes-execution-assignment";

    @Test
    void readyExecutionWithPlanningAssignmentsCannotBeDeletedOrChangeRouting() throws Exception {
        try (var c = context()) {
            var m = MaterialIssueHttpTest.mvc(c);
            var j = c.getBean(MaterialIssueHttpTest.Fixture.class).business.get("A");
            j.update("INSERT INTO mes_execution_assignment(execution_id,routing_node_id,user_id,assigned_by,assigned_at) VALUES(1,1,1,1,CURRENT_TIMESTAMP)");
            m.perform(full(delete("/api/production/mes-production-execution"), "A").content("[1]"))
                    .andExpect(status().isConflict()).andExpect(jsonPath("errorCode").value("EXECUTION_REFERENCED"));
            m.perform(full(put("/api/production/mes-production-execution/1"), "A").content("{\"workorderId\":\"1\",\"routingId\":\"2\",\"targetQty\":\"100\",\"status\":\"READY\"}"))
                    .andExpect(status().isConflict()).andExpect(jsonPath("errorCode").value("EXECUTION_FROZEN"));
            assertEquals(1, j.queryForObject("SELECT COUNT(*) FROM mes_execution_assignment WHERE execution_id=1", Integer.class));
            assertEquals(1, j.queryForObject("SELECT routing_id FROM mes_production_execution WHERE id=1", Integer.class));
        }
    }

    @Test
    void scopePermissionsDuplicateAndTransactionalBatch() throws Exception {
        try (var c = context()) {
            var http = MaterialIssueHttpTest.mvc(c);
            http.perform(post(C).header("Authorization", "Bearer CRUD").header("X-Factory-Id", "A")
                            .contentType("application/json").content("{\"operationId\":1,\"userId\":1}"))
                    .andExpect(status().isForbidden());
            http.perform(post(C + "/bind-users").header("Authorization", "Bearer FULL").header("X-Factory-Id", "A")
                            .contentType("application/json").content("{\"operationId\":1,\"userIds\":[1,2]}"))
                    .andExpect(status().isNoContent());
            http.perform(get(C).header("Authorization", "Bearer CRUD").header("X-Factory-Id", "A")
                            .param("operationId", "1").param("limit", "1")).andExpect(status().isOk())
                    .andExpect(jsonPath("total").value(2)).andExpect(jsonPath("items.length()").value(1));
            http.perform(post(C).header("Authorization", "Bearer FULL").header("X-Factory-Id", "A")
                            .contentType("application/json").content("{\"operationId\":1,\"userId\":1}"))
                    .andExpect(status().isConflict());
            // New user for another operation before a duplicate; complete batch must roll back.
            http.perform(post(C).header("Authorization", "Bearer FULL").header("X-Factory-Id", "A")
                            .contentType("application/json").content("{\"operationId\":2,\"userId\":2}"))
                    .andExpect(status().isOk());
            http.perform(post(C + "/bind-users").header("Authorization", "Bearer FULL").header("X-Factory-Id", "A")
                            .contentType("application/json").content("{\"operationId\":2,\"userIds\":[1,2]}"))
                    .andExpect(status().isConflict());
            http.perform(get(C).header("Authorization", "Bearer FULL").header("X-Factory-Id", "A")
                    .param("operationId", "2")).andExpect(status().isOk()).andExpect(jsonPath("total").value(1));
            http.perform(get(C).header("Authorization", "Bearer FULL").header("X-Factory-Id", "B"))
                    .andExpect(status().isOk()).andExpect(jsonPath("total").value(0));
            http.perform(post(A).header("Authorization", "Bearer FULL").header("X-Factory-Id", "B")
                            .contentType("application/json").content("{\"executionId\":1,\"routingNodeId\":1,\"userId\":1}"))
                    .andExpect(status().isUnprocessableEntity());
            http.perform(post(C).header("Authorization", "Bearer FULL").header("X-Factory-Id", "A")
                            .contentType("application/json").content("{\"operationId\":1,\"userId\":999}"))
                    .andExpect(status().isUnprocessableEntity());
            http.perform(post(C).header("Authorization", "Bearer FULL").header("X-Factory-Id", "A")
                            .contentType("application/json").content("{\"operationId\":999,\"userId\":1}"))
                    .andExpect(status().isUnprocessableEntity());
        }
    }

    @Test
    void distinctNodesMultiPersonTerminalAndEligibility() throws Exception {
        try (var c = context()) {
            var http = MaterialIssueHttpTest.mvc(c);
            http.perform(post(C + "/bind-users").header("Authorization", "Bearer FULL").header("X-Factory-Id", "A")
                            .contentType("application/json").content("{\"operationId\":1,\"userIds\":[1,2]}"))
                    .andExpect(status().isNoContent());
            String body = "{\"executionId\":1,\"routingNodeId\":1,\"userId\":1}";
            http.perform(post(A).header("Authorization", "Bearer FULL").header("X-Factory-Id", "A")
                            .contentType("application/json").content(body)).andExpect(status().isOk())
                    .andExpect(jsonPath("assignedBy").value("1")).andExpect(jsonPath("assignedAt").isNotEmpty());
            http.perform(post(A).header("Authorization", "Bearer FULL").header("X-Factory-Id", "A")
                    .contentType("application/json").content(body)).andExpect(status().isConflict());
            http.perform(post(A).header("Authorization", "Bearer FULL").header("X-Factory-Id", "A")
                            .contentType("application/json").content(body.replace("\"userId\":1", "\"userId\":2")))
                    .andExpect(status().isOk());
            http.perform(post(A).header("Authorization", "Bearer FULL").header("X-Factory-Id", "A")
                    .contentType("application/json").content(body.replace("\"routingNodeId\":1", "\"routingNodeId\":2")
                            .replace("\"userId\":1", "\"userId\":2"))).andExpect(status().isOk());
            http.perform(post(A).header("Authorization", "Bearer FULL").header("X-Factory-Id", "A")
                            .contentType("application/json")
                            .content(body.replace("\"routingNodeId\":1", "\"routingNodeId\":3")))
                    .andExpect(status().isUnprocessableEntity());
            http.perform(post(A).header("Authorization", "Bearer FULL").header("X-Factory-Id", "A")
                            .contentType("application/json").content(body.replace("\"executionId\":1", "\"executionId\":2")))
                    .andExpect(status().isConflict());
            var eligibility = c.getBean(ReportOperatorEligibilityService.class);
            FactoryContext.runWithScope(new FactoryScope(new FactoryId("A")), () -> {
                eligibility.requireEligible(1L, 1L, 1L);
                assertEquals(ErrorCode.OPERATOR_NOT_ASSIGNED, assertThrows(FoundationException.class, () -> eligibility.requireEligible(1L, 2L, 1L)).code());
            });
            http.perform(delete(A).header("Authorization", "Bearer CRUD").header("X-Factory-Id", "A")
                    .contentType("application/json").content("[1]")).andExpect(status().isForbidden());
            http.perform(get(A).header("Authorization", "Bearer FULL").header("X-Factory-Id", "A")
                            .param("executionId", "1").param("routingNodeId", "1")).andExpect(status().isOk())
                    .andExpect(jsonPath("total").value(2));
            http.perform(get("/api/production/executions/1/assignment-nodes").header("Authorization", "Bearer FULL")
                            .header("X-Factory-Id", "A")).andExpect(status().isOk())
                    .andExpect(jsonPath("nodes.length()").value(2));
            http.perform(get("/api/production/operator-candidates").header("Authorization", "Bearer FULL")
                            .header("X-Factory-Id", "A").param("operationId", "1")).andExpect(status().isOk())
                    .andExpect(jsonPath("items.length()").value(2));
        }
    }
}
