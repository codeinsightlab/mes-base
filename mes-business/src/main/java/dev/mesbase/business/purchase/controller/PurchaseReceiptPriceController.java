package dev.mesbase.business.purchase.controller;

import dev.mesbase.business.purchase.service.IPurchaseReferencePriceService;
import dev.mesbase.business.purchase.service.impl.PurchasePriceRules;
import dev.mesbase.common.utils.bean.BeanConversions;
import dev.mesbase.business.purchase.controller.vo.PurchaseReferencePriceVo;
import dev.mesbase.platform.web.EndpointAccess;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.springframework.web.bind.annotation.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

@RestController
@RequestMapping("/api/purchase/receipt-price")
@ConditionalOnProperty(name = "mes.business.enabled", havingValue = "true")
public class PurchaseReceiptPriceController {
    private final IPurchaseReferencePriceService service;

    public PurchaseReceiptPriceController(IPurchaseReferencePriceService service) {
        this.service = service;
    }

    @GetMapping
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED, permission = "business:purchase:reference:price:query")
    public PricePreview preview(@RequestParam Long supplierId, @RequestParam Long materialId,
                                @RequestParam LocalDate date, @RequestParam(required = false) BigDecimal quantity,
                                @RequestParam(required = false) BigDecimal unitPrice,
                                @RequestParam(required = false) String remark) {
        return BeanConversions.convert(service.preview(supplierId, materialId, date, quantity, unitPrice, remark), PricePreview.class);
    }

    public record PricePreview(PurchaseReferencePriceVo reference, String amount) {
    }
}
