package dev.issuefixture;

import dev.mesbase.business.production.service.ProductionReceiptService;
import dev.mesbase.business.production.service.ProductionIdentityService;
import dev.mesbase.business.production.service.ProductionReportService;
import dev.mesbase.business.production.service.ReportUserReferenceService;
import dev.mesbase.business.production.domain.ProductionReportModels.Adjustment;
import dev.mesbase.business.quality.domain.ProductionDefectModels.Command;
import dev.mesbase.business.quality.domain.ProductionDefectModels.Facts;
import java.time.LocalDateTime;

import static org.mockito.Mockito.mock;

import dev.mesbase.business.production.domain.ProductionReceiptModels.Receive;
import dev.mesbase.business.production.domain.ProductionReceiptModels.Reverse;
import dev.mesbase.business.production.domain.ProductionReceiptModels.Query;
import dev.mesbase.core.error.FoundationException;
import dev.mesbase.core.error.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.Callable;
import dev.mesbase.common.core.domain.entity.SysUser;
import dev.mesbase.common.core.domain.model.LoginUser;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.yaml.snakeyaml.Yaml;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * LIVE MYSQL. Only new isolated TEST samples; seeded historical receipt fixtures are not successful receive commands.
 */
@EnabledIfEnvironmentVariable(named = "MES_RECEIPT_MYSQL_ACCEPTANCE", matches = "true")
class ProductionReceiptMySqlTest {
    @Test
    void immutableFixturesReplayQualityBlockExcessAndConcurrentReversals() throws Exception {
        try (var h = new ProductionQualityLifecycleMySqlTest.Harness(); var pool = Executors.newFixedThreadPool(2)) {
            var dir = Path.of(".local/receipt-v1-20261016");
            Files.createDirectories(dir);
            var before = h.jdbc.queryForList("SELECT * FROM mes_production_receipt ORDER BY id");
            Files.writeString(dir.resolve("mysql-existing-receipts.yml"), new Yaml().dump(before));
            var inventoryBefore = h.jdbc.queryForList("SELECT * FROM wm_inventory ORDER BY id");
            Files.writeString(dir.resolve("mysql-existing-inventory.yml"), new Yaml().dump(inventoryBefore));
            var f = h.fixture();
            ProductionInputTestData.confirmed(h.jdbc, f.execution(), new BigDecimal("100"));
            h.jdbc.update("UPDATE mes_production_execution SET target_qty=100,status='RUNNING',start_time=NOW(),remark='TEST ONLY Receipt V1 historical fixture' WHERE id=?", f.execution());
            h.jdbc.update("UPDATE mes_execution_path SET path_status='LOCKED',locked_at=NOW() WHERE id=?", f.execution());
            for (Long node : f.selected())
                h.jdbc.update("INSERT INTO mes_production_report(execution_id,routing_node_id,record_type,quantity_delta,operator_user_id,recorded_by,report_time,reason,create_time) VALUES(?,?,'REPORT',100,100,100,NOW(),'TEST ONLY Receipt historical fixture gross quantity',NOW())", f.execution(), node);
            h.context.registerBean(ProductionIdentityService.class);
            h.context.registerBean(ProductionReceiptService.class);
            var service = h.context.getBean(ProductionReceiptService.class);
            assertEquals(0, service.summary(f.execution()).output().compareTo(new BigDecimal("100")));
            var fail = assertThrows(FoundationException.class, () -> service.receive(new Receive(f.execution(), f.warehouse(), null, BigDecimal.ONE, UUID.randomUUID()
                    .toString())));
            assertEquals(ErrorCode.RECEIPT_QUALITY_UNVERIFIED, fail.code());
            assertEquals(0, h.jdbc.queryForObject("SELECT COUNT(*) FROM mes_production_receipt WHERE execution_id=?", Integer.class, f.execution()));
            String original = UUID.randomUUID().toString();
            h.jdbc.update("INSERT INTO mes_production_receipt(id,receipt_no,execution_id,warehouse_id,product_lot_code,record_type,quantity_delta,operator_user_id,recorded_by,occurred_at,create_time,reason) VALUES(?,?,?,?,'TEST-LOT','RECEIPT',90,100,100,NOW(3),NOW(3),'TEST ONLY historical receipt fixture; not actual handover')", original, "TEST_FIXTURE_" + original, f.execution(), f.warehouse());
            var same = new Receive(f.execution(), f.warehouse(), "TEST-LOT", new BigDecimal("90.000000"), original);
            assertEquals(original, service.receive(same).getId());
            assertThrows(FoundationException.class, () -> service.receive(new Receive(f.execution(), f.warehouse(), "TEST-LOT", BigDecimal.ONE, original)));
            assertThrows(FoundationException.class, () -> service.receive(new Receive(f.execution() + 1, f.warehouse(), "TEST-LOT", new BigDecimal("90"), original)));
            var user = new SysUser();
            user.setUserId(101L);
            var login = new LoginUser(101L, 1L, user, Set.of());
            SecurityContextHolder.getContext()
                    .setAuthentication(new UsernamePasswordAuthenticationToken(login, null, List.of()));
            assertThrows(FoundationException.class, () -> service.receive(same));
            ProductionReportMySqlTest.actor();
            assertThrows(Exception.class, () -> h.jdbc.update("UPDATE mes_production_receipt SET quantity_delta=1 WHERE id=?", original));
            assertThrows(Exception.class, () -> h.jdbc.update("DELETE FROM mes_production_receipt WHERE id=?", original));
            assertThrows(Exception.class, () -> h.jdbc.update("DELETE FROM mes_production_execution WHERE id=?", f.execution()));
            // Real upstream immutable adjustment lowers terminal output to 85; Receipt never forbids it.
            Long terminal = f.selected().getLast();
            h.context.registerBean(ReportUserReferenceService.class, () -> mock(ReportUserReferenceService.class));
            h.context.registerBean(ProductionReportService.class);
            var reportService = h.context.getBean(ProductionReportService.class);
            Long originalReport = h.jdbc.queryForObject("SELECT id FROM mes_production_report WHERE execution_id=? AND routing_node_id=? AND record_type='REPORT'", Long.class, f.execution(), terminal);
            reportService.adjust(originalReport, new Adjustment(new BigDecimal("-15"), "TEST ONLY Receipt upstream correction"));
            var summary = service.summary(f.execution());
            assertEquals(0, summary.excess().compareTo(new BigDecimal("5")));
            assertEquals(0, summary.remaining().compareTo(new BigDecimal("-5")));
            String replayKey = UUID.randomUUID().toString();
            var barrier = new CyclicBarrier(2);
            Callable<String> replay = () -> {
                ProductionReportMySqlTest.actor();
                try {
                    barrier.await();
                    return service.reverse(original, new Reverse(new BigDecimal("5"), "TEST ONLY excess reversal", replayKey))
                            .getId();
                } finally {
                    SecurityContextHolder.clearContext();
                }
            };
            var a = pool.submit(replay);
            var b = pool.submit(replay);
            assertEquals(a.get(), b.get());
            assertEquals(1, h.jdbc.queryForObject("SELECT COUNT(*) FROM mes_production_receipt WHERE id=?", Integer.class, replayKey));
            assertEquals(0, service.summary(f.execution()).excess().signum());
            service.reverse(original, new Reverse(new BigDecimal("65"), "TEST ONLY partial", UUID.randomUUID()
                    .toString()));
            var gate = new CyclicBarrier(2);
            Callable<Integer> overdraw = () -> {
                ProductionReportMySqlTest.actor();
                try {
                    gate.await();
                    service.reverse(original, new Reverse(new BigDecimal("15"), "TEST ONLY concurrent", UUID.randomUUID()
                            .toString()));
                    return 200;
                } catch (FoundationException e) {
                    return e.code().httpStatus();
                } finally {
                    SecurityContextHolder.clearContext();
                }
            };
            var x = pool.submit(overdraw);
            var y = pool.submit(overdraw);
            var statuses = List.of(x.get(), y.get());
            assertTrue(statuses.contains(200));
            assertTrue(statuses.contains(409));
            assertThrows(FoundationException.class, () -> service.reverse(replayKey, new Reverse(BigDecimal.ONE, "TEST ONLY reject reversal of reversal", UUID.randomUUID()
                    .toString())));
            service.reverse(original, new Reverse(new BigDecimal("5"), "TEST ONLY fixture end full reversal", UUID.randomUUID()
                    .toString()));
            assertEquals(0, service.detail(original).originalBalance().signum());
            assertEquals(0, service.summary(f.execution()).received().signum());
            // Real quality create/revise/void changes O once, without a second Receipt subtraction.
            var facts = new Facts(f.execution(), terminal, terminal, null, new BigDecimal("2"), "PROCESS", "TEST_RECEIPT_QUALITY", null, null, "WAREHOUSE", null, f.warehouse(), "TEST ONLY receipt quantity integration", LocalDateTime.of(2026, 1, 1, 10, 0));
            var quality = h.quality.create(new Command("RECEIPT_Q_" + UUID.randomUUID(), null, null, facts)).record();
            h.declarations.add(quality.getDeclarationId());
            assertEquals(0, service.summary(f.execution()).output().compareTo(new BigDecimal("83")));
            var revised = new Facts(f.execution(), terminal, terminal, null, new BigDecimal("3"), "PROCESS", "TEST_RECEIPT_QUALITY", null, null, "WAREHOUSE", null, f.warehouse(), "TEST ONLY revised quantity", facts.occurredAt());
            h.quality.revise(quality.getDeclarationId(), new Command("RECEIPT_Q_REV_" + UUID.randomUUID(), 1, "TEST ONLY revise", revised));
            assertEquals(0, service.summary(f.execution()).output().compareTo(new BigDecimal("82")));
            h.quality.voidDeclaration(quality.getDeclarationId(), new Command("RECEIPT_Q_VOID_" + UUID.randomUUID(), 2, "TEST ONLY fixture ended", null));
            assertEquals(0, service.summary(f.execution()).output().compareTo(new BigDecimal("85")));
            assertEquals(inventoryBefore, h.jdbc.queryForList("SELECT * FROM wm_inventory ORDER BY id"));
            assertEquals("RUNNING", h.jdbc.queryForObject("SELECT status FROM mes_production_execution WHERE id=?", String.class, f.execution()));
            var rows = service.page(new Query(f.order(), f.execution(), null, null, null, 0, 20));
            assertEquals(5, rows.total());
            // Historical user rows and quantities are retained, never deleted for test cleanup.
            Files.writeString(dir.resolve("mysql-receipt-fixture.yml"), new Yaml().dump(Map.of("executionId", f.execution(), "workorderId", f.order(), "warehouseId", f.warehouse(), "originalId", original, "tag", f.tag(), "positiveReceive", "BLOCKED_QUALITY", "netReceived", 0, "rows", rows.total())));
            Files.writeString(dir.resolve("mysql-receipts-after.yml"), new Yaml().dump(h.jdbc.queryForList("SELECT * FROM mes_production_receipt WHERE execution_id=? ORDER BY create_time,id", f.execution())));
            for (var old : before)
                assertEquals(old, h.jdbc.queryForMap("SELECT * FROM mes_production_receipt WHERE id=?", old.get("id")));
        }
    }
}
