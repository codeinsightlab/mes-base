package dev.issuefixture;

import dev.mesbase.business.production.service.BusinessSourceRules;
import org.mockito.Mockito;
import dev.mesbase.business.wm.service.LocalStockService;
import dev.mesbase.business.wm.service.LocalProductionSupply;
import dev.mesbase.business.wm.service.StockReferenceRules;
import dev.mesbase.business.wm.service.impl.WmInventoryServiceImpl;
import dev.mesbase.business.wm.service.IWmInventoryService;
import dev.mesbase.business.wm.sync.WmInventoryQueryPort;
import dev.mesbase.business.wm.domain.StockCommand;
import dev.mesbase.business.wm.domain.StockActionType;
import dev.mesbase.business.wm.domain.WmInventoryCreateCommand;
import dev.mesbase.business.wm.domain.WmInventoryUpdateCommand;
import dev.mesbase.business.wm.dao.WmInventoryMapper;
import dev.mesbase.business.integration.warehouse.WarehouseIntegration;
import dev.mesbase.business.issue.service.MaterialIssueRules;
import dev.mesbase.business.issue.service.MaterialIssueErpFacts;
import dev.mesbase.business.issue.service.MaterialIssueInputFacts;
import dev.mesbase.business.issue.service.MaterialIssueSubmissionService;
import dev.mesbase.business.issue.service.MaterialIssueVoidService;
import dev.mesbase.business.issue.service.impl.MesMaterialIssueServiceImpl;
import dev.mesbase.business.issue.service.impl.MesMaterialIssueItemServiceImpl;
import dev.mesbase.business.production.service.ProductionInputService;
import dev.mesbase.business.production.service.ProductionInputSupplyService;
import dev.mesbase.business.production.service.ProductionIdentityService;
import dev.mesbase.business.production.service.IRoutingService;
import dev.mesbase.core.factory.FactoryScope;
import dev.mesbase.core.factory.FactoryId;
import dev.mesbase.core.error.FoundationException;
import dev.mesbase.platform.execution.FactoryContext;
import dev.mesbase.infrastructure.cache.BusinessCacheProcessor;
import dev.mesbase.framework.sync.SyncCoordinator;
import java.math.BigDecimal;
import java.util.UUID;
import java.util.Map;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.CyclicBarrier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.core.env.MapPropertySource;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

/**
 * LIVE MYSQL: verified SIMPLE_B source, real generated persistence/transactions, isolated TEST records retained.
 */
@EnabledIfEnvironmentVariable(named = "MES_WAREHOUSE_MYSQL_ACCEPTANCE", matches = "true")
class LocalWarehouseMySqlTest {
    @Test
    void committedClosureRemainsAt615ForUserVerification() throws Exception {
        verifyClosure(false);
    }

    @Test
    void committedReplayConcurrencyAndNetReversal() throws Exception {
        verifyClosure(true);
    }

    void verifyClosure(boolean reversal) throws Exception {
        try (var h = new ProductionQualityLifecycleMySqlTest.Harness(); var pool = Executors.newFixedThreadPool(2)) {
            var f = h.fixture();
            var c = h.context;
            var j = h.jdbc;
            long material = j.queryForObject("SELECT product_id FROM workorder WHERE id=?", Long.class, f.order());
            String code = j.queryForObject("SELECT code FROM material WHERE id=?", String.class, material);
            String lot = "TEST_WAREHOUSE_" + UUID.randomUUID();
            String prefix = "mes.production.input.factories.SIMPLE_B.workorders." + f.order() + ".";
            c.getEnvironment().getPropertySources().addFirst(new MapPropertySource("TEST ONLY warehouse recipe", Map.of(
                    prefix + "material-id", Long.toString(material),
                    prefix + "total-required-qty", "20", prefix + "warehouse-ids", Long.toString(f.warehouse()), prefix + "work-center", "TEST_WAREHOUSE_CENTER")));
            for (var type : new Class<?>[]{WarehouseIntegration.class, LocalStockService.class, LocalProductionSupply.class,
                    StockReferenceRules.class, MaterialIssueRules.class, MaterialIssueErpFacts.class, MaterialIssueInputFacts.class,
                    MesMaterialIssueServiceImpl.class, MesMaterialIssueItemServiceImpl.class, MaterialIssueSubmissionService.class,
                    MaterialIssueVoidService.class, ProductionInputSupplyService.class, ProductionInputService.class, ProductionIdentityService.class})
                c.registerBean(type);
            c.registerBean(IRoutingService.class, () -> mock(IRoutingService.class));
            c.registerBean(WmInventoryServiceImpl.class, () -> new WmInventoryServiceImpl(c.getBean(WmInventoryMapper.class),
                    c.getBeanProvider(WmInventoryQueryPort.class), mock(SyncCoordinator.class), c.getBeanProvider(IWmInventoryService.class),
                    Validation.buildDefaultValidatorFactory()
                            .getValidator(), mock(BusinessCacheProcessor.class), c.getBean(StockReferenceRules.class), c.getBean(WarehouseIntegration.class), c.getBean(BusinessSourceRules.class)));
            var scope = new FactoryScope(new FactoryId("SIMPLE_B"));
            FactoryContext.runWithScope(scope, () -> {
                assertEquals(0, j.queryForObject("SELECT COUNT(*) FROM wm_inventory WHERE batch_no=?", Integer.class, lot));
                var crud = c.getBean(IWmInventoryService.class);
                var service = c.getBean(LocalStockService.class);
                var stock = crud.insert(new WmInventoryCreateCommand(material, f.warehouse(), BigDecimal.ZERO, BigDecimal.ZERO, "ENABLED", "TEST ONLY local warehouse closure", lot, "TEST_BIN"));
                long id = stock.inventory().getId();
                assertThrows(FoundationException.class, () -> crud.insert(new WmInventoryCreateCommand(material, f.warehouse(), BigDecimal.ZERO, BigDecimal.ZERO, "ENABLED", "TEST duplicate source", lot, "TEST_BIN")));
                service.execute(StockActionType.OPENING, command(id, "500", "0", null));
                var receipt = command(id, "300", "500", null);
                service.execute(StockActionType.GAIN, receipt);
                service.execute(StockActionType.GAIN, receipt);
                assertThrows(FoundationException.class, () -> service.execute(StockActionType.GAIN, new StockCommand(receipt.requestId(), id, new BigDecimal("301"), receipt.expectedQuantity(), receipt.expectedAvailable(), receipt.reason(), null)));
                var supply = c.getBean(ProductionInputSupplyService.class);
                var production = c.getBean(ProductionInputService.class);
                var calculation = supply.calculate(code + "#" + lot, f.order(), Long.toString(id), new BigDecimal("100"));
                var issue = production.submit(new ProductionInputService.Command(UUID.randomUUID()
                        .toString(), code + "#" + lot, f.order(), Long.toString(id), new BigDecimal("100"), f.routing(), f.selected(), "PRODUCTION_INPUT", ProductionInputSupplyService.calculationKey(calculation)));
                long issueId = Long.parseLong(issue.issueId());
                balance(j, id, "600");
                var back = command(id, "20", "600", issueId);
                service.execute(StockActionType.RETURN, back);
                service.execute(StockActionType.RETURN, back);
                service.execute(StockActionType.LOSS, command(id, "5", "620", null));
                balance(j, id, "615");
                assertEquals(5, j.queryForObject("SELECT COUNT(*) FROM wm_stock_action WHERE stock_id=?", Integer.class, id));
                assertThrows(FoundationException.class, () -> service.execute(StockActionType.RETURN, command(id, "181", "615", issueId)));
                assertThrows(FoundationException.class, () -> crud.updateById(id, new WmInventoryUpdateCommand(material, f.warehouse(), BigDecimal.ZERO, BigDecimal.ZERO, "ENABLED", null, lot, "TEST_BIN")));
                assertThrows(FoundationException.class, () -> crud.deleteByIds(List.of(id)));
                assertThrows(RuntimeException.class, () -> j.update("UPDATE wm_stock_action SET reason='TEST forbidden' WHERE id=?", receipt.requestId()));
                assertThrows(RuntimeException.class, () -> j.update("DELETE FROM wm_stock_action WHERE id=?", receipt.requestId()));
                try {
                    Files.writeString(Path.of(".local/warehouse-closure-20261017/mysql-closure-result.txt"),
                            "SIMPLE_B datasource8 stock=" + id + " lot=" + lot + " execution=" + issue.executionId() + " issue=" + issueId + " closure=615 actions=5 retained=" + !reversal + "\n",
                            StandardOpenOption.CREATE, StandardOpenOption.APPEND);
                } catch (Exception failure) {
                    throw new IllegalStateException(failure);
                }
                if (!reversal) return;
                var concurrent = command(id, "7", "615", null);
                var barrier = new CyclicBarrier(2);
                var x = pool.submit(() -> {
                    ProductionReportMySqlTest.actor();
                    barrier.await();
                    return FactoryContext.withScope(scope, () -> service.execute(StockActionType.GAIN, concurrent));
                });
                var y = pool.submit(() -> {
                    ProductionReportMySqlTest.actor();
                    barrier.await();
                    return FactoryContext.withScope(scope, () -> service.execute(StockActionType.GAIN, concurrent));
                });
                try {
                    assertEquals(x.get().getId(), y.get().getId());
                } catch (Exception failure) {
                    throw new IllegalStateException(failure);
                }
                balance(j, id, "622");
                c.getBean(MaterialIssueVoidService.class).cancel(issueId);
                balance(j, id, "802");
                c.getBean(MaterialIssueVoidService.class).cancel(issueId);
                balance(j, id, "802");
                assertEquals(7, j.queryForObject("SELECT COUNT(*) FROM wm_stock_action WHERE stock_id=?", Integer.class, id));
                assertEquals(0, j.queryForObject("SELECT COUNT(*) FROM wm_stock_action WHERE stock_id=? AND (quantity_after<>quantity_before+quantity_delta OR available_after<>available_before+available_delta)", Integer.class, id));
                try {
                    Files.writeString(Path.of(".local/warehouse-closure-20261017/mysql-reversal-result.txt"), "SIMPLE_B datasource8 192.168.18.25:3306/mes_base_dev_business_simple_b\nTEST stock=" + id + " lot=" + lot + " execution=" + issue.executionId() + " issue=" + issueId + "\n500 -> 800 -> 600 -> 620 -> 615; 5 actions; concurrent replay +7 once ->622; net reversal +180 once ->802; 7 immutable actions; zero role grants\n");
                } catch (Exception failure) {
                    throw new IllegalStateException(failure);
                }
            });
        }
    }

    static StockCommand command(long stock, String quantity, String before, Long issue) {
        return new StockCommand(UUID.randomUUID()
                .toString(), stock, new BigDecimal(quantity), new BigDecimal(before), new BigDecimal(before), "TEST ONLY local warehouse closure", issue);
    }

    static void balance(JdbcTemplate j, long id, String expected) {
        assertEquals(0, new BigDecimal(expected).compareTo(j.queryForObject("SELECT quantity FROM wm_inventory WHERE id=?", BigDecimal.class, id)));
    }
}
