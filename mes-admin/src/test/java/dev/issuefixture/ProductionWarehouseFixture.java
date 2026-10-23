package dev.issuefixture;

import java.math.BigDecimal;
import org.springframework.test.web.servlet.MockMvc;
import dev.mesbase.business.production.controller.ProductionReceiptController;
import dev.mesbase.business.production.service.ProductionReceiptService;
import dev.mesbase.business.production.dao.ProductionReceiptQueryMapper;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.MapPropertySource;
import org.springframework.mock.web.MockServletContext;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

/**
 * TEST ONLY two routed H2 databases, real Spring locks/XML; historical receipt fixtures explicitly seeded.
 */
class ProductionWarehouseFixture {
    static final String ROOT = "/api/production/receipts";

    @Configuration
    @Import({ProductionReportHttpTest.Extra.class, ProductionReceiptService.class, ProductionReceiptController.class})
    static class Extra {
        @Bean
        ProductionReceiptQueryMapper receiptQueries(SqlSessionFactory f) {
            return new SqlSessionTemplate(f).getMapper(ProductionReceiptQueryMapper.class);
        }
    }

    static AnnotationConfigWebApplicationContext context(Class<?>... additional) throws Exception {
        var c = new AnnotationConfigWebApplicationContext();
        c.setServletContext(new MockServletContext());
        c.getEnvironment().getPropertySources()
                .addFirst(new MapPropertySource("TEST ONLY", Map.of("mes.business.enabled", "true")));
        c.register(MaterialIssueHttpTest.Web.class, Extra.class, ProductionDefectHttpTest.Extra.class);
        if (additional.length > 0) c.register(additional);
        c.refresh();
        for (var j : c.getBean(MaterialIssueHttpTest.Fixture.class).business.values()) {
            ProductionReportHttpTest.seed(j);
            j.update("UPDATE mes_production_execution SET batch_no='TEST-FINISHED' WHERE id IN (1,2)");
            ProductionInputTestData.confirmed(j, 1, new BigDecimal("100"));
            j.update("UPDATE mes_production_execution SET target_qty=100,status='RUNNING',start_time=CURRENT_TIMESTAMP WHERE id=1");
            j.update("INSERT INTO mes_production_report(execution_id,routing_node_id,record_type,quantity_delta,operator_user_id,recorded_by,report_time,reason,create_time) VALUES(1,11,'REPORT',100,1,1,CURRENT_TIMESTAMP,'TEST ONLY',CURRENT_TIMESTAMP),(1,12,'REPORT',100,1,1,CURRENT_TIMESTAMP,'TEST ONLY',CURRENT_TIMESTAMP),(1,13,'REPORT',100,1,1,CURRENT_TIMESTAMP,'TEST ONLY',CURRENT_TIMESTAMP)");
        }
        return c;
    }

    static JdbcTemplate db(AnnotationConfigWebApplicationContext c) {
        return c.getBean(MaterialIssueHttpTest.Fixture.class).business.get("A");
    }

    static String seed(JdbcTemplate j, String qty) {
        String id = UUID.randomUUID().toString();
        j.update("INSERT INTO mes_production_receipt(id,receipt_no,execution_id,warehouse_id,record_type,quantity_delta,operator_user_id,recorded_by,occurred_at,create_time,product_lot_code) VALUES(?,?,1,1,'RECEIPT',?,100,1,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,'TEST-LOT')", id, "TEST_ONLY_" + id, qty);
        return id;
    }

    static String reverse(String qty, String key) {
        return "{\"quantity\":\"" + qty + "\",\"reason\":\"TEST ONLY冲销\",\"requestId\":\"" + key + "\",\"recordedBy\":999}";
    }

    /**
     * TEST ONLY seed pre-convergence history, never a current business command.
     */
    static String legacy(MockMvc m, JdbcTemplate j) throws Exception {
        j.update("INSERT INTO wm_inventory(id,material_id,warehouse_id,batch_no,quantity,available_quantity,status) VALUES(1,1,1,'TEST-FINISHED',0,0,'ENABLED')");
        String id = seed(j, "80");
        return id;
    }

    static String legacyAccepted(JdbcTemplate j, String receipt, String qty) {
        String id = UUID.randomUUID().toString();
        j.update("INSERT INTO mes_warehouse_acceptance(id,receipt_id,execution_id,product_id,stock_id,quantity_delta,stock_action_id,request_hash,operator_user_id,recorded_at,reason) VALUES(?,?,1,1,1,?,?,?,1,CURRENT_TIMESTAMP,'TEST ONLY pre-convergence fact')", id, receipt, qty, id, "0".repeat(64));
        j.update("INSERT INTO wm_stock_action(id,request_hash,stock_id,material_id,unit_id,warehouse_id,batch_no,action_type,quantity_delta,available_delta,quantity_before,quantity_after,available_before,available_after,source_type,source_id,execution_id,operator_user_id,occurred_at,recorded_at,reason) VALUES(?,?,1,1,1,1,'TEST-FINISHED','PRODUCT_RECEIPT',?,?,0,?,0,?,'PRODUCTION_RECEIPT',?,1,1,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,'TEST ONLY historical stock fact')", id, "0".repeat(64), qty, qty, qty, qty, receipt);
        j.update("UPDATE wm_inventory SET quantity=?,available_quantity=? WHERE id=1", qty, qty);
        return id;
    }
}
