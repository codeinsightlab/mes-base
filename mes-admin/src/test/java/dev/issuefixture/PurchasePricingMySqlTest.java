package dev.issuefixture;

import dev.mesbase.business.purchase.domain.*;
import dev.mesbase.business.purchase.service.*;
import dev.mesbase.business.wm.domain.*;
import dev.mesbase.business.wm.service.StockDocumentService;
import dev.mesbase.acceptance.FactorySnapshotFixture;
import dev.mesbase.platform.execution.FactoryIntegrationContext;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import static org.junit.jupiter.api.Assertions.*;
import static dev.issuefixture.PurchaseReceiptMySqlTest.*;

/**
 * Opt-in committed MySQL business acceptance. Uses existing formal tables and tagged records only.
 */
@EnabledIfEnvironmentVariable(named = "MES_PURCHASE_MYSQL_ACCEPTANCE", matches = "true")
class PurchasePricingMySqlTest {
    static PurchaseSupplierCreateCommand supplier(String code, String name) {
        return new PurchaseSupplierCreateCommand(code, name, "QA联系人", "TEST ONLY", "TEST ONLY", "ENABLED", "QA price acceptance");
    }

    static PurchaseSupplierUpdateCommand change(PurchaseSupplierEntity s, String name, String status) {
        return new PurchaseSupplierUpdateCommand(s.getCode(), name, s.getContactPerson(), s.getContactPhone(), s.getAddress(), status, s.getRemark());
    }

    static PurchaseReferencePriceCreateCommand quote(long supplier, Stock stock, String price, String date) {
        return new PurchaseReferencePriceCreateCommand(supplier, stock.material(), stock.unit(), new BigDecimal(price), "CNY", LocalDate.parse(date), "QA effective date test");
    }

    @Test
    void committedPriceHistoryTransactionsSnapshotsAndLegacyRead() throws Exception {
        try (var f = context()) {
            assertEquals(1, f.db()
                    .queryForObject("SELECT COUNT(*) FROM flyway_business_schema_history WHERE version='87' AND success=1", Integer.class));
            var suppliers = f.context().getBean(IPurchaseSupplierService.class);
            var prices = f.context().getBean(IPurchaseReferencePriceService.class);
            var documents = f.context().getBean(StockDocumentService.class);
            String tag = "QA_PRICE_" + UUID.randomUUID().toString().substring(0, 8);
            var legacyBefore = f.db()
                    .queryForList("SELECT id,supplier_name,supplier_id,business_date,currency,status FROM wm_stock_document WHERE document_type='PURCHASE_RECEIPT' AND status='POSTED' AND supplier_id IS NULL ORDER BY id");
            assertFalse(legacyBefore.isEmpty(), "V86 posted records must be present for compatibility proof");
            var a = actor(() -> suppliers.insert(supplier(tag + "_A", "QA原名称")));
            var b = actor(() -> suppliers.insert(supplier(tag + "_B", "QA另一供应商")));
            assertThrows(RuntimeException.class, () -> actor(() -> suppliers.insert(supplier(tag + "_A", "重复编码"))));
            var master = f.db()
                    .queryForList("SELECT m.id,m.unit_id FROM material m JOIN unit_measure u ON u.id=m.unit_id WHERE m.status='ENABLED' AND u.status='ENABLED' ORDER BY m.id LIMIT 2");
            long warehouse = f.db()
                    .queryForObject("SELECT id FROM wm_warehouse WHERE status='ENABLED' AND source='LOCAL' ORDER BY id LIMIT 1", Long.class);
            var stock1 = stock(f, ((Number) master.get(0).get("id")).longValue(), ((Number) master.get(0)
                    .get("unit_id")).longValue(), warehouse, tag + "_1");
            var stock2 = stock(f, ((Number) master.get(1).get("id")).longValue(), ((Number) master.get(1)
                    .get("unit_id")).longValue(), warehouse, tag + "_2");
            var first = actor(() -> prices.insert(quote(a.getId(), stock1, "1.111111", "2026-11-01")));
            var revised = actor(() -> prices.insert(quote(a.getId(), stock1, "2.222222", "2026-11-01")));
            actor(() -> prices.insert(quote(a.getId(), stock1, "9.999999", "2026-12-01")));
            actor(() -> prices.insert(quote(b.getId(), stock1, "7.777777", "2026-11-01")));
            actor(() -> prices.insert(quote(a.getId(), stock2, "3.333333", "2026-11-01")));
            assertEquals(revised.getId(), actor(() -> prices.effective(a.getId(), stock1.material(), LocalDate.of(2026, 11, 6))).getId());
            assertEquals(first.getCreatedAt().toLocalDate(), LocalDate.now());
            assertNull(actor(() -> prices.effective(a.getId(), stock1.material(), LocalDate.of(2026, 10, 31))));
            assertEquals(3, f.db()
                    .queryForObject("SELECT COUNT(*) FROM purchase_reference_price WHERE supplier_id=? AND material_id=?", Integer.class, a.getId(), stock1.material()));
            var preview = actor(() -> prices.preview(a.getId(), stock1.material(), LocalDate.of(2026, 11, 6), new BigDecimal("3"), new BigDecimal("1.005"), null));
            assertEquals(0, new BigDecimal("3.02").compareTo(preview.amount()));
            var actual1 = new StockDocumentModels.AggregateLine(null, stock1.id(), stock1.material(), stock1.warehouse(), stock1.unit(), stock1.batch(), null, new BigDecimal("3"), "QA actual negotiated price", new BigDecimal("1.005"));
            var actual2 = new StockDocumentModels.AggregateLine(null, stock2.id(), stock2.material(), stock2.warehouse(), stock2.unit(), stock2.batch(), null, new BigDecimal("2"), "QA reference suggestion", null);
            var doc = draft(documents, tag, List.of(actual1, actual2), a.getId());
            assertEquals("QA原名称", doc.document().getSupplierName());
            assertEquals(revised.getId(), doc.lines().get(0).getReferencePriceId());
            assertEquals(0, new BigDecimal("3.02").compareTo(doc.lines().get(0).getAmount()));
            assertEquals(0, new BigDecimal("6.67").compareTo(doc.lines().get(1).getAmount()));
            assertEquals(0, balance(f, stock1).signum());
            assertEquals(0, actions(f, doc.document().getId()));
            assertThrows(RuntimeException.class, () -> actor(() -> {
                suppliers.deleteByIds(List.of(a.getId()));
                return null;
            }));
            actor(() -> suppliers.updateById(a.getId(), change(a, "QA改名并停用", "DISABLED")));
            // Existing draft remains usable after disabling; a new receipt and quotation are rejected.
            assertThrows(RuntimeException.class, () -> draft(documents, tag + "_DISABLED", List.of(actual1), a.getId()));
            assertThrows(RuntimeException.class, () -> actor(() -> prices.insert(quote(a.getId(), stock1, "5", "2026-11-01"))));
            var intent = new StockDocumentModels.Post(UUID.randomUUID().toString(), doc.document().getRevision());
            actor(() -> documents.approve(doc.document().getId(), intent));
            actor(() -> documents.approve(doc.document().getId(), intent));
            assertEquals(0, new BigDecimal("3").compareTo(balance(f, stock1)));
            assertEquals(0, new BigDecimal("2").compareTo(balance(f, stock2)));
            assertEquals(2, actions(f, doc.document().getId()));
            actor(() -> suppliers.updateById(a.getId(), change(a, "QA调价后名称", "ENABLED")));
            actor(() -> prices.insert(quote(a.getId(), stock1, "88", "2026-11-01")));
            var frozen = actor(() -> documents.detail(doc.document().getId()));
            var history = actor(() -> prices.selectPage(new PurchaseReferencePriceQueryDto(0, 20, a.getId(),
                    stock1.material(), null, null, null, null, null)));
            assertEquals(4, history.total());
            assertEquals("QA调价后名称", history.items().getFirst().getSupplierName());
            assertNotNull(history.items().getFirst().getMaterialName());
            assertNotNull(history.items().getFirst().getUnitName());

            assertEquals("QA原名称", frozen.document().getSupplierName());
            assertEquals(0, new BigDecimal("1.005").compareTo(frozen.lines().get(0).getUnitPrice()));
            assertEquals(0, new BigDecimal("3.02").compareTo(frozen.lines().get(0).getAmount()));
            assertEquals(revised.getId(), frozen.lines().get(0).getReferencePriceId());
            for (var old : legacyBefore) {
                var read = actor(() -> documents.detail(((Number) old.get("id")).longValue()));
                assertNull(read.document().getSupplierId());
                assertEquals(old.get("supplier_name"), read.document().getSupplierName());
                assertEquals("POSTED", read.document().getStatus());
            }
            assertEquals(legacyBefore, f.db()
                    .queryForList("SELECT id,supplier_name,supplier_id,business_date,currency,status FROM wm_stock_document WHERE document_type='PURCHASE_RECEIPT' AND status='POSTED' AND supplier_id IS NULL ORDER BY id"));
            var unused = actor(() -> suppliers.insert(supplier(tag + "_DELETE", "QA未引用")));
            actor(() -> {
                suppliers.deleteByIds(List.of(unused.getId()));
                return null;
            });
            assertThrows(RuntimeException.class, () -> actor(() -> suppliers.selectById(unused.getId())));
            assertThrows(RuntimeException.class, () -> actor(() -> {
                FactoryIntegrationContext.run(FactorySnapshotFixture.erp(FACTORY, "E10", "TEST"), () -> prices.insert(quote(a.getId(), stock1, "6", "2026-11-01")));
                return null;
            }));
            String evidence = "database=mes_base_dev_perf_test factory=" + FACTORY + "\ntag=" + tag + " supplier=" + a.getId() + "," + b.getId() + "\n"
                    + "quotes=" + f.db()
                    .queryForList("SELECT id,supplier_id,material_id,unit_id,unit_price,currency,effective_date,created_at FROM purchase_reference_price WHERE supplier_id IN (?,?) ORDER BY id", a.getId(), b.getId()) + "\n"
                    + "header=" + f.db()
                    .queryForList("SELECT id,document_no,supplier_id,supplier_name,business_date,currency,status FROM wm_stock_document WHERE id=?", doc.document()
                            .getId()) + "\n"
                    + "lines=" + f.db()
                    .queryForList("SELECT id,material_id,stock_id,quantity,unit_price,amount,reference_price_id,reference_unit_price FROM wm_stock_document_line WHERE document_id=?", doc.document()
                            .getId()) + "\n"
                    + "before=0,0 draft_actions=0 after=" + balance(f, stock1) + "," + balance(f, stock2) + "\n"
                    + "actions=" + f.db()
                    .queryForList("SELECT id,stock_id,quantity_delta,quantity_before,quantity_after,document_line_id FROM wm_stock_action WHERE document_line_id IN (SELECT id FROM wm_stock_document_line WHERE document_id=?)", doc.document()
                            .getId()) + "\nlegacyV86=" + legacyBefore + " unchanged\n";
            var path = Path.of(".local/knowledge/purchase-price-v1/mysql-prices-" + tag + ".txt");
            Files.createDirectories(path.getParent());
            Files.writeString(path, evidence);
            System.out.println(evidence);
        }
    }
}
