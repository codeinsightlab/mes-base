package dev.issuefixture;

import dev.mesbase.business.integration.warehouse.WarehouseIntegration;
import dev.mesbase.business.wm.service.LocalProductionSupply;
import dev.mesbase.business.production.controller.ProductionInputController;
import dev.mesbase.business.production.service.ProductionInputService;
import dev.mesbase.business.production.service.ProductionIdentityService;
import dev.mesbase.business.production.service.ProductionInputSupplyService;
import dev.mesbase.core.factory.FactoryId;
import dev.mesbase.core.factory.FactoryScope;
import dev.mesbase.platform.execution.FactoryContext;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.core.env.MapPropertySource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.mock;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

/**
 * Real SIMPLE_B read-only MVC GET. Does not seed, edit, migrate, or invoke production commands.
 */
@EnabledIfEnvironmentVariable(named = "MES_STABILITY_READ_MYSQL", matches = "true")
class ProductionStabilitySupplyMySqlTest {
    @Test
    void existing101ReleasedOrdersDoNotBroadenTheScannedProductionOrder() throws Exception {
        try (var c = ProductionDefectMySqlTest.context()) {
            c.getEnvironment().getPropertySources().addFirst(new MapPropertySource("TEST ONLY read supply", Map.of(
                    "mes.production.input.factories.SIMPLE_B.workorders.117.material-id", "1",
                    "mes.production.input.factories.SIMPLE_B.workorders.117.total-required-qty", "20",
                    "mes.production.input.factories.SIMPLE_B.workorders.117.warehouse-ids", "9",
                    "mes.production.input.factories.SIMPLE_B.workorders.117.work-center", "TEST_LOCAL_CENTER")));
            c.registerBean(WarehouseIntegration.class);
            c.registerBean(LocalProductionSupply.class);
            c.registerBean(ProductionInputSupplyService.class);
            c.registerBean(ProductionIdentityService.class);
            var jdbc = c.getBean(JdbcTemplate.class);
            long count = jdbc.queryForObject("SELECT COUNT(*) FROM workorder WHERE status='RELEASED'", Long.class);
            assertTrue(count >= 101);
            var code = jdbc.queryForObject("SELECT CONCAT(w.code,':',m.code,'#',s.batch_no) FROM workorder w JOIN material m ON m.id=w.product_id JOIN wm_inventory s ON s.id=13 WHERE w.id=117", String.class);
            var mvc = MockMvcBuilders.standaloneSetup(new ProductionInputController(c.getBean(ProductionInputSupplyService.class), mock(ProductionInputService.class)))
                    .build();
            var scope = new FactoryScope(new FactoryId("SIMPLE_B"));
            FactoryContext.withScope(scope, () -> {
                try {
                    mvc.perform(get("/api/production/input").param("code", code))
                            .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(1))
                            .andExpect(jsonPath("$.orders[0].workorderId").value("117"));
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
                return null;
            });
            assertEquals(count, jdbc.queryForObject("SELECT COUNT(*) FROM workorder WHERE status='RELEASED'", Long.class));
        }
    }
}
