package dev.issuefixture;

import dev.mesbase.business.production.service.ProductionInputService;
import dev.mesbase.business.production.service.ProductionInputSupplyService;
import dev.mesbase.business.production.service.ProductionIdentityService;
import dev.mesbase.business.production.service.IRoutingService;
import dev.mesbase.business.production.service.impl.MesProductionExecutionServiceImpl;
import dev.mesbase.business.issue.service.impl.MesMaterialIssueServiceImpl;
import dev.mesbase.business.issue.service.impl.MesMaterialIssueItemServiceImpl;
import dev.mesbase.business.issue.service.MaterialIssueRules;
import dev.mesbase.business.issue.service.MaterialIssueErpFacts;
import dev.mesbase.business.issue.service.MaterialIssueInputFacts;
import dev.mesbase.business.issue.service.MaterialIssueSubmissionService;
import dev.mesbase.business.issue.service.MaterialIssueVoidService;
import dev.mesbase.business.integration.warehouse.WarehouseIntegration;
import dev.mesbase.business.wm.service.LocalProductionSupply;
import dev.mesbase.business.wm.service.LocalStockService;
import dev.mesbase.business.production.service.ProductionQuantityService;
import dev.mesbase.core.factory.FactoryScope;
import dev.mesbase.core.factory.FactoryId;
import dev.mesbase.platform.execution.FactoryContext;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import org.springframework.core.env.MapPropertySource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import static org.mockito.Mockito.mock;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.mesbase.core.error.FoundationException;

/**
 * Real designated MySQL, current local MES use cases. No ERP calls or customer data mutations.
 */
@EnabledIfEnvironmentVariable(named = "MES_INPUT_MYSQL_ACCEPTANCE", matches = "true")
class ProductionInputMySqlTest {
    @Test
    void localProductionInputsAreAtomicRepeatableAndPermanentlyBound() throws Exception {
        try (var h = new ProductionQualityLifecycleMySqlTest.Harness(); var pool = Executors.newFixedThreadPool(2)) {
            var f = h.fixture();
            var c = h.context;
            var j = h.jdbc;
            long material = j.queryForObject("SELECT product_id FROM workorder WHERE id=?", Long.class, f.order());
            String materialCode = j.queryForObject("SELECT code FROM material WHERE id=?", String.class, material);
            String lot = "TEST_INPUT_" + UUID.randomUUID();
            long stock = ProductionReportMySqlTest.insert(j, "INSERT INTO wm_inventory(material_id,warehouse_id,quantity,available_quantity,status,batch_no,remark) VALUES(?,?,10000,10000,'ENABLED',?,'TEST ONLY local production input')", material, f.warehouse(), lot);
            String prefix = "mes.production.input.factories.SIMPLE_B.workorders." + f.order() + ".";
            c.getEnvironment().getPropertySources()
                    .addFirst(new MapPropertySource("TEST ONLY local input recipe", Map.of(
                            "mes.integration.warehouse.factories.SIMPLE_B.mode", "LOCAL_ONLY", prefix + "material-id", Long.toString(material), prefix + "total-required-qty", "20", prefix + "warehouse-ids", Long.toString(f.warehouse()), prefix + "work-center", "TEST_LOCAL_CENTER")));
            c.registerBean(IRoutingService.class, () -> mock(IRoutingService.class));
            for (var type : new Class<?>[]{LocalStockService.class, LocalProductionSupply.class, WarehouseIntegration.class, MaterialIssueRules.class, MaterialIssueErpFacts.class, MaterialIssueInputFacts.class, MesProductionExecutionServiceImpl.class, MesMaterialIssueServiceImpl.class, MesMaterialIssueItemServiceImpl.class, MaterialIssueSubmissionService.class, MaterialIssueVoidService.class, ProductionInputSupplyService.class, ProductionInputService.class, ProductionIdentityService.class})
                c.registerBean(type);
            var service = c.getBean(ProductionInputService.class);
            var supply = c.getBean(ProductionInputSupplyService.class);
            var quantity = c.getBean(ProductionQuantityService.class);
            var scope = new FactoryScope(new FactoryId("SIMPLE_B"));
            var a = FactoryContext.withScope(scope, () -> {
                var calculation = supply.calculate(materialCode + "#" + lot, f.order(), Long.toString(stock), new BigDecimal("100"));
                return new ProductionInputService.Command(UUID.randomUUID()
                        .toString(), materialCode + "#" + lot, f.order(), Long.toString(stock), new BigDecimal("100"), f.routing(), f.selected(), "PRODUCTION_INPUT", ProductionInputSupplyService.calculationKey(calculation));
            });
            var first = FactoryContext.withScope(scope, () -> service.submit(a));
            long execution = Long.parseLong(first.executionId());
            assertEquals(first, FactoryContext.withScope(scope, () -> service.submit(a)));
            assertEquals(0, quantity.getExecutionQuantities(execution).productionInputQty()
                    .compareTo(new BigDecimal("100")));
            FactoryContext.runWithScope(scope, () -> h.paths.start(execution));
            j.update("INSERT INTO mes_production_report(execution_id,routing_node_id,record_type,quantity_delta,operator_user_id,recorded_by,report_time,reason,create_time) VALUES(?,?,'REPORT',80,100,100,NOW(),'TEST ONLY input integration report fixture',NOW())", execution, f.selected()
                    .getFirst());
            var b = FactoryContext.withScope(scope, () -> {
                var calculation = supply.calculate(a.code(), f.order(), Long.toString(stock), new BigDecimal("200"));
                return new ProductionInputService.Command(UUID.randomUUID()
                        .toString(), a.code(), f.order(), a.stockReference(), new BigDecimal("200"), f.routing(), f.selected(), "PRODUCTION_INPUT", ProductionInputSupplyService.calculationKey(calculation));
            });
            var repeated = FactoryContext.withScope(scope, () -> service.submit(b));
            assertEquals(first.executionId(), repeated.executionId());
            assertEquals(0, quantity.getExecutionQuantities(execution).nodes().getFirst().availableQty()
                    .compareTo(new BigDecimal("220")));
            assertEquals(0, j.queryForObject("SELECT target_qty FROM mes_production_execution WHERE id=?", BigDecimal.class, execution)
                    .compareTo(new BigDecimal("100")));
            assertEquals(0, j.queryForObject("SELECT quantity FROM wm_inventory WHERE id=?", BigDecimal.class, stock)
                    .compareTo(new BigDecimal("9400")));
            var x = pool.submit(() -> {
                ProductionReportMySqlTest.actor();
                return FactoryContext.withScope(scope, () -> service.submit(b));
            });
            var y = pool.submit(() -> {
                ProductionReportMySqlTest.actor();
                return FactoryContext.withScope(scope, () -> service.submit(b));
            });
            x.get();
            y.get();
            assertEquals(2, j.queryForObject("SELECT COUNT(*) FROM mes_material_issue_input WHERE execution_id=?", Integer.class, execution));
            long original = j.queryForObject("SELECT id FROM mes_production_report WHERE execution_id=? AND record_type='REPORT'", Long.class, execution);
            j.update("INSERT INTO mes_production_report(execution_id,routing_node_id,record_type,quantity_delta,reference_report_id,operator_user_id,recorded_by,report_time,reason,create_time) VALUES(?,?,'ADJUSTMENT',200,?,100,100,NOW(),'TEST ONLY immutable adjustment fixture',NOW())", execution, f.selected()
                    .getFirst(), original);
            var voids = c.getBean(MaterialIssueVoidService.class);
            assertThrows(FoundationException.class, () -> FactoryContext.withScope(scope, () -> voids.cancel(Long.valueOf(first.issueId()))));
            Files.writeString(Path.of(".local/production-input-20261016/mysql-input-result.txt"), "SIMPLE_B datasource=8; LOCAL_ONLY real MySQL; execution=" + execution + "; issue1=" + first.issueId() + "; issue2=" + repeated.issueId() + "; code=" + first.code() + "; input=300; plan=100; stock=9400; replay requests=2\n");
        }
    }
}
