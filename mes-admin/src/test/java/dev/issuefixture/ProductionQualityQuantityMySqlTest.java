package dev.issuefixture;

import dev.issuefixture.ProductionQualityLifecycleMySqlTest.Harness;
import dev.issuefixture.ProductionQualityLifecycleMySqlTest.Fixture;
import dev.mesbase.business.production.service.ProductionReportService;
import dev.mesbase.business.production.service.ReportUserReferenceService;
import dev.mesbase.business.production.service.ProductionQuantityService;
import dev.mesbase.business.production.domain.ProductionReportModels.Supplement;
import dev.mesbase.business.production.domain.ProductionReportModels.Adjustment;
import dev.mesbase.business.quality.domain.ProductionDefectModels.Command;
import dev.mesbase.core.error.FoundationException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static dev.issuefixture.ProductionQualityLifecycleMySqlTest.await;

/**
 * LIVE MySQL: verified SIMPLE_B, new identities only, real services/transactions/row locks.
 */
@EnabledIfEnvironmentVariable(named = "MES_QUALITY_QUANTITY_MYSQL", matches = "true")
@Timeout(90)
class ProductionQualityQuantityMySqlTest {
    static ProductionReportService reports(Harness h) {
        h.context.registerBean(ReportUserReferenceService.class);
        h.context.registerBean(ProductionReportService.class);
        return h.context.getBean(ProductionReportService.class);
    }

    static Fixture ready(Harness h) {
        var f = h.fixture();
        ProductionInputTestData.confirmed(h.jdbc, f.execution(), BigDecimal.TEN);
        h.jdbc.update("UPDATE mes_execution_path SET path_status='LOCKED',locked_at=NOW() WHERE id=?", f.execution());
        return f;
    }

    static long report(ProductionReportService r, Fixture f, long node, String qty) {
        return r.supplement(new Supplement(f.execution(), node, new BigDecimal(qty), 100L, LocalDateTime.of(2026, 1, 1, 10, 0), null, "TEST ONLY Phase 2B gross processed"))
                .getId();
    }

    static String create(Harness h, Fixture f, long node, String qty) {
        var id = h.quality.create(ProductionDefectMySqlTest.command("QTY_" + UUID.randomUUID(), 0, f.execution(), node, qty))
                .record().getDeclarationId();
        synchronized (h.declarations) {
            h.declarations.add(id);
        }
        return id;
    }

    static void adjust(ProductionReportService r, long id, String delta) {
        r.adjust(id, new Adjustment(new BigDecimal(delta), "TEST ONLY Phase 2B adjustment"));
    }

    static int run(Supplier<?> action) {
        ProductionReportMySqlTest.actor();
        try {
            action.get();
            return 200;
        } catch (FoundationException e) {
            return e.code().httpStatus();
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    /**
     * First writer holds its DB lock until the second has tried to enter.
     */
    static void ordered(Harness h, Supplier<?> first, Supplier<?> second, int secondStatus) throws Exception {
        try (var pool = Executors.newFixedThreadPool(2)) {
            var held = new CountDownLatch(1);
            var release = new CountDownLatch(1);
            var entered = new CountDownLatch(1);
            var a = pool.submit(() -> run(() -> h.transaction(() -> {
                var result = first.get();
                held.countDown();
                await(release);
                return result;
            })));
            try {
                await(held);
                var b = pool.submit(() -> {
                    entered.countDown();
                    return run(second);
                });
                await(entered);
                assertThrows(TimeoutException.class, () -> b.get(200, TimeUnit.MILLISECONDS));
                release.countDown();
                assertEquals(200, a.get(10, TimeUnit.SECONDS));
                assertEquals(secondStatus, b.get(10, TimeUnit.SECONDS));
            } finally {
                release.countDown();
            }
        }
        ProductionReportMySqlTest.actor();
    }

    @Test
    void qualityCreateVersusNegativeReportAdjustmentBothOrders() throws Exception {
        try (var h = new Harness()) {
            var r = reports(h);
            for (boolean qualityFirst : List.of(true, false)) {
                var f = ready(h);
                long original = report(r, f, f.selected().getFirst(), "10");
                Supplier<Object> q = () -> create(h, f, f.selected().getFirst(), "2");
                Supplier<Object> a = () -> {
                    adjust(r, original, "-9");
                    return null;
                };
                ordered(h, qualityFirst ? q : a, qualityFirst ? a : q, 409);
                h.invariant(f);
                h.context.getBean(ProductionQuantityService.class).getExecutionQuantities(f.execution());
            }
        }
    }

    @Test
    void upstreamDefectVersusDownstreamReportBothOrders() throws Exception {
        try (var h = new Harness()) {
            var r = reports(h);
            for (boolean qualityFirst : List.of(true, false)) {
                var f = ready(h);
                report(r, f, f.selected().getFirst(), "10");
                Supplier<Object> q = () -> create(h, f, f.selected().getFirst(), "3");
                Supplier<Object> b = () -> report(r, f, f.selected().get(1), "8");
                ordered(h, qualityFirst ? q : b, qualityFirst ? b : q, qualityFirst ? 409 : 200);
                h.invariant(f);
                var view = h.context.getBean(ProductionQuantityService.class).getExecutionQuantities(f.execution());
                assertEquals(0, BigDecimal.valueOf(qualityFirst ? 0 : 1)
                        .compareTo(view.nodes().get(1).historicalOverrunQty()));
            }
        }
    }

    @Test
    void twoQualityCreatesCannotOverdrawSameProcessedQuantity() throws Exception {
        try (var h = new Harness()) {
            var f = ready(h);
            var r = reports(h);
            report(r, f, f.selected().getFirst(), "10");
            ordered(h, () -> create(h, f, f.selected().getFirst(), "6"), () -> create(h, f, f.selected()
                    .getFirst(), "6"), 409);
            assertEquals(0, new BigDecimal("6").compareTo(h.quality.sumEffectiveDefectQty(f.execution(), f.selected()
                    .getFirst())));
        }
    }

    @Test
    void concurrentRevisionsReplaceContributionAndKeepExpectedRevision() throws Exception {
        try (var h = new Harness()) {
            var f = ready(h);
            report(reports(h), f, f.selected().getFirst(), "10");
            var id = create(h, f, f.selected().getFirst(), "1");
            ordered(h, () -> h.quality.revise(id, ProductionDefectMySqlTest.command("REV_" + UUID.randomUUID(), 1, f.execution(), f.selected()
                            .getFirst(), "2")),
                    () -> h.quality.revise(id, ProductionDefectMySqlTest.command("REV_" + UUID.randomUUID(), 1, f.execution(), f.selected()
                            .getFirst(), "3")), 409);
            assertEquals(2, h.quality.latest(id).getRevisionNo());
            assertEquals(0, new BigDecimal("2").compareTo(h.quality.sumEffectiveDefectQty(f.execution(), f.selected()
                    .getFirst())));
        }
    }

    @Test
    void voidVersusReportAdjustmentSerializesWithoutLosingHistory() throws Exception {
        try (var h = new Harness()) {
            var f = ready(h);
            var r = reports(h);
            long original = report(r, f, f.selected().getFirst(), "10");
            var id = create(h, f, f.selected().getFirst(), "5");
            ordered(h, () -> h.quality.voidDeclaration(id, new Command("VOID_" + UUID.randomUUID(), 1, "TEST ONLY释放容量", null)),
                    () -> {
                        adjust(r, original, "-8");
                        return null;
                    }, 200);
            assertEquals(2, h.quality.latest(id).getRevisionNo());
            h.invariant(f);
            assertEquals(0, new BigDecimal("2").compareTo(h.context.getBean(ProductionQuantityService.class)
                    .getExecutionQuantities(f.execution()).nodes().getFirst().reportedQty()));
        }
    }

    @Test
    void differentWorkordersDoNotShareAnExecutionQuantityLock() throws Exception {
        try (var h = new Harness(); var pool = Executors.newFixedThreadPool(2)) {
            var r = reports(h);
            var one = ready(h);
            var two = ready(h);
            report(r, one, one.selected().getFirst(), "10");
            report(r, two, two.selected().getFirst(), "10");
            var held = new CountDownLatch(1);
            var release = new CountDownLatch(1);
            var a = pool.submit(() -> run(() -> h.transaction(() -> {
                var id = create(h, one, one.selected().getFirst(), "2");
                held.countDown();
                await(release);
                return id;
            })));
            try {
                await(held);
                var b = pool.submit(() -> run(() -> create(h, two, two.selected().getFirst(), "2")));
                assertEquals(200, b.get(3, TimeUnit.SECONDS));
                assertTrue(!a.isDone());
                release.countDown();
                assertEquals(200, a.get(10, TimeUnit.SECONDS));
            } finally {
                release.countDown();
            }
        }
    }
}
