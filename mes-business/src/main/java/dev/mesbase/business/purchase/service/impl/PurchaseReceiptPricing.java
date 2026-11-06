package dev.mesbase.business.purchase.service.impl;

import dev.mesbase.business.purchase.service.IPurchaseReferencePriceService;
import dev.mesbase.business.purchase.service.IPurchaseSupplierService;
import dev.mesbase.business.wm.domain.WmStockDocumentEntity;
import dev.mesbase.business.wm.domain.WmStockDocumentLineEntity;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

/**
 * Snapshot operations join the existing document transaction; they never post inventory.
 */
@Service
@ConditionalOnProperty(name = "mes.business.enabled", havingValue = "true")
public class PurchaseReceiptPricing {
    private final IPurchaseSupplierService suppliers;
    private final IPurchaseReferencePriceService prices;

    public PurchaseReceiptPricing(IPurchaseSupplierService suppliers, IPurchaseReferencePriceService prices) {
        this.suppliers = suppliers;
        this.prices = prices;
    }

    public void header(WmStockDocumentEntity row, Long supplierId, LocalDate date, String currency) {
        if (date == null) PurchasePriceRules.reject("采购业务日期不能为空");
        PurchasePriceRules.currency(currency);
        boolean changed = !Objects.equals(row.getSupplierId(), supplierId);
        var supplier = suppliers.lock(supplierId, changed || row.getId() == null);
        if (changed || row.getId() == null) row.setSupplierName(supplier.getName());
        row.setSupplierId(supplierId);
        row.setBusinessDate(date);
        row.setCurrency(currency);
    }

    public void line(WmStockDocumentEntity header, WmStockDocumentLineEntity line, BigDecimal actual) {
        var quote = prices.effective(header.getSupplierId(), line.getMaterialId(), header.getBusinessDate());
        line.setReferencePriceId(null);
        line.setReferenceUnitPrice(null);
        if (quote != null) {
            if (!Objects.equals(quote.getUnitId(), line.getUnitId()) || !Objects.equals(quote.getCurrency(), header.getCurrency()))
                PurchasePriceRules.reject("参考报价的单位或币种与采购单不一致，请追加正确报价");
            line.setReferencePriceId(quote.getId());
            line.setReferenceUnitPrice(quote.getUnitPrice());
            if (actual == null) actual = quote.getUnitPrice();
        }
        line.setUnitPrice(PurchasePriceRules.price(actual, line.getRemark()));
        line.setAmount(PurchasePriceRules.amount(line.getQuantity(), line.getUnitPrice()));
    }

    public void validatePosted(WmStockDocumentEntity header, java.util.List<WmStockDocumentLineEntity> lines) {
        // Disabled suppliers remain valid on existing documents; current quotes never rewrite their snapshots.
        suppliers.lock(header.getSupplierId(), false);
        if (header.getBusinessDate() == null) PurchasePriceRules.reject("请补全采购业务日期");
        PurchasePriceRules.currency(header.getCurrency());
        for (var line : lines) {
            PurchasePriceRules.price(line.getUnitPrice(), line.getRemark());
            if (line.getAmount() == null || line.getAmount()
                    .compareTo(PurchasePriceRules.amount(line.getQuantity(), line.getUnitPrice())) != 0)
                PurchasePriceRules.reject("采购成交金额与数量、单价不一致，请重新保存草稿");
        }
    }
}
