package dev.issuefixture;

import static dev.issuefixture.ProductionCoreV1HttpTest.full;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import dev.mesbase.business.basic.dao.MaterialBomMapper;
import dev.mesbase.business.basic.dao.MaterialMapper;
import dev.mesbase.business.basic.service.MaterialBomService;
import dev.mesbase.business.production.domain.WorkOrderBomBuildModels;
import dev.mesbase.business.production.service.WorkOrderBomBuildService;
import dev.mesbase.business.production.service.WorkOrderService;
import dev.mesbase.system.service.AutoCodeNumbering;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.Map;
import java.util.TreeMap;
import java.sql.Connection;
import org.apache.ibatis.executor.statement.StatementHandler;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.plugin.*;
import org.apache.ibatis.reflection.SystemMetaObject;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import tools.jackson.databind.ObjectMapper;

/**
 * Real HTTP/services/Mapper SQL with routed H2 and guarded center numbering, never deployed-service proof.
 */
class WorkOrderBomBuildHttpTest {
    private static final String ROOT = "/api/production/workorder/bom-build";
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final WorkOrderBomBuildModels.PreviewRequest SOURCE =
            new WorkOrderBomBuildModels.PreviewRequest(10001L, 10001L, new BigDecimal("2"));

    @Configuration
    static class Extra {
        @Bean
        @Primary
        WorkOrderBomBuildService realBomBuildService(MaterialBomService boms, MaterialBomMapper versions,
                                                     MaterialMapper materials, WorkOrderService orders,
                                                     AutoCodeNumbering codes,
                                                     @Qualifier("businessTransactionManager") PlatformTransactionManager transactions) {
            return new WorkOrderBomBuildService(boms, versions, materials, orders, codes, transactions);
        }
    }

    @Test
    void httpConfirmationUsesOwnedReadsAndOneBatchAndReplaysWithoutTouchingOtherFactory() throws Exception {
        try (var context = ProductionInputHttpTest.context(Extra.class, ManufacturedInputSourceHttpTest.Extra.class)) {
            var db = ProductionInputHttpTest.db(context);
            seed(db);
            var other = context.getBean(MaterialIssueHttpTest.Fixture.class).business.get("B");
            int otherOrders = other.queryForObject("SELECT COUNT(*) FROM workorder", Integer.class);
            int beforeOrders = db.queryForObject("SELECT COUNT(*) FROM workorder", Integer.class);
            int beforeLines = db.queryForObject("SELECT COUNT(*) FROM mes_workorder_material", Integer.class);
            var mvc = MaterialIssueHttpTest.mvc(context);
            var trace = new SqlTrace();
            context.getBean(SqlSessionFactory.class).getConfiguration().addInterceptor(trace);
            trace.phase = "preview";
            var preview = JSON.readValue(mvc.perform(full(post(ROOT + "/preview"))
                            .content(JSON.writeValueAsString(SOURCE))).andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString(), WorkOrderBomBuildModels.Preview.class);
            assertTrue(preview.complete());
            assertEquals(5, preview.orders().size());
            assertEquals(3, preview.orders().getFirst().materials().size());
            var body = JSON.writeValueAsString(confirmation(preview));
            trace.phase = "confirm";
            String first = mvc.perform(full(post(ROOT + "/confirm")).content(body))
                    .andExpect(status().isOk()).andExpect(jsonPath("orders.length()").value(5))
                    .andReturn().getResponse().getContentAsString();
            trace.phase = "replay";
            String replay = mvc.perform(full(post(ROOT + "/confirm")).content(body))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            trace.print();
            assertEquals(JSON.readTree(first), JSON.readTree(replay));
            assertEquals(beforeOrders + 5, db.queryForObject("SELECT COUNT(*) FROM workorder", Integer.class));
            assertEquals(beforeLines + 7, db.queryForObject("SELECT COUNT(*) FROM mes_workorder_material", Integer.class));
            assertEquals(4, db.queryForObject("SELECT COUNT(*) FROM workorder WHERE bom_build_parent_workorder_id IS NOT NULL", Integer.class));
            assertEquals(0, db.queryForObject("SELECT COUNT(*) FROM workorder child LEFT JOIN workorder parent ON parent.id=child.bom_build_parent_workorder_id WHERE child.bom_build_parent_workorder_id IS NOT NULL AND parent.id IS NULL", Integer.class));
            assertEquals(otherOrders, other.queryForObject("SELECT COUNT(*) FROM workorder", Integer.class));
            assertEquals(0, other.queryForObject("SELECT COUNT(*) FROM workorder WHERE bom_build_id IS NOT NULL", Integer.class));
            Long rootId = db.queryForObject("SELECT id FROM workorder WHERE bom_build_path='ROOT'", Long.class);
            mvc.perform(full(get("/api/production/input/manufactured-sources")).param("workorderId", rootId.toString()))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("errorCode").value("PRODUCTION_SINGLE_MATERIAL_REQUIRED"));
        }
    }

    @Test
    void httpStaleBomReturnsBusinessConflictAndCreatesNoOrders() throws Exception {
        try (var context = ProductionInputHttpTest.context(Extra.class)) {
            var db = ProductionInputHttpTest.db(context);
            seed(db);
            int beforeOrders = db.queryForObject("SELECT COUNT(*) FROM workorder", Integer.class);
            int beforeLines = db.queryForObject("SELECT COUNT(*) FROM mes_workorder_material", Integer.class);
            var mvc = MaterialIssueHttpTest.mvc(context);
            var preview = JSON.readValue(mvc.perform(full(post(ROOT + "/preview"))
                            .content(JSON.writeValueAsString(SOURCE))).andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString(), WorkOrderBomBuildModels.Preview.class);
            db.update("UPDATE material_bom SET revision=revision+1 WHERE id=10004");
            mvc.perform(full(post(ROOT + "/confirm")).content(JSON.writeValueAsString(confirmation(preview))))
                    .andExpect(status().isConflict()).andExpect(jsonPath("errorCode").value("PRODUCTION_INPUT_STALE"));
            assertEquals(beforeOrders, db.queryForObject("SELECT COUNT(*) FROM workorder", Integer.class));
            assertEquals(beforeLines, db.queryForObject("SELECT COUNT(*) FROM mes_workorder_material", Integer.class));
        }
    }

    private static WorkOrderBomBuildModels.Confirmation confirmation(WorkOrderBomBuildModels.Preview preview) {
        var choices = preview.orders().stream().map(order -> new WorkOrderBomBuildModels.OrderChoice(
                order.path(), null, null, null, null, "TEST ONLY HTTP BOM build")).toList();
        return new WorkOrderBomBuildModels.Confirmation(SOURCE,
                new WorkOrderBomBuildModels.Confirm(UUID.randomUUID().toString(), preview.fingerprint(), choices));
    }

    @Intercepts(@Signature(type = StatementHandler.class, method = "prepare", args = {Connection.class, Integer.class}))
    public static class SqlTrace implements Interceptor {
        String phase;
        final Map<String, Map<String, Integer>> counts = new TreeMap<>();

        public Object intercept(Invocation invocation) throws Throwable {
            var statement = (MappedStatement) SystemMetaObject.forObject(invocation.getTarget())
                    .getValue("delegate.mappedStatement");
            counts.computeIfAbsent(phase, ignored -> new TreeMap<>()).merge(statement.getId(), 1, Integer::sum);
            return invocation.proceed();
        }

        void print() {
            counts.forEach((key, values) -> System.out.println("BOM_HTTP_SQL " + key + " total="
                    + values.values().stream().mapToInt(Integer::intValue).sum() + " " + values));
        }
    }

    private static void seed(JdbcTemplate db) {
        // Rule candidates/numbering use the shared fixture's guarded center adapter, not production rule storage.
        for (long id : List.of(10001L, 10002L, 10003L, 10004L, 10005L, 10006L, 10007L, 10008L)) {
            db.update("INSERT INTO material(id,code,name,unit_id,status,work_order_rule_code) VALUES(?,?,?,1,'ENABLED','MATERIAL_ISSUE')",
                    id, "BOM_HTTP_" + id, "TEST ONLY BOM HTTP " + id);
        }
        for (long id : List.of(10001L, 10002L, 10003L, 10004L)) {
            db.update("INSERT INTO material_bom(id,code,version,product_id,base_qty,unit_id,status,revision,created_by,created_at) VALUES(?,?,'1',?,1,1,'PUBLISHED',1,1,CURRENT_TIMESTAMP)",
                    id, "BOM_HTTP_" + id, id);
        }
        db.update("INSERT INTO material_bom_item(id,bom_id,material_id,quantity,unit_id,child_bom_id,sort) VALUES "
                + "(10001,10001,10002,2,1,10002,1),(10002,10001,10003,3,1,10003,2),(10003,10001,10008,0.5,1,NULL,3),"
                + "(10004,10002,10004,2,1,10004,1),(10005,10003,10004,1,1,10004,1),(10006,10004,10007,3,1,NULL,1)");
    }
}
