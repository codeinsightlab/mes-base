package dev.mesbase.business.production.controller;

import dev.mesbase.business.production.domain.MesProductionReceiptEntity;
import dev.mesbase.business.production.domain.ProductionReceiptModels.Query;
import dev.mesbase.business.production.domain.ProductionReceiptRow;
import dev.mesbase.business.production.service.ProductionReceiptService;
import dev.mesbase.common.utils.bean.BeanConversions;
import dev.mesbase.platform.web.EndpointAccess;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Read-only legacy logistics history.
 */
@RestController
@RequestMapping("/api/production/receipts")
@ConditionalOnProperty(name = "mes.business.enabled", havingValue = "true")
public class ProductionReceiptController {
    public static final String LIST = "business:production:receipt:list";
    public static final String DETAIL = "business:production:receipt:detail";
    private final ProductionReceiptService service;

    public ProductionReceiptController(ProductionReceiptService service) {
        this.service = service;
    }

    public record Ledger(String id, String receiptNo, String executionId, String executionNo, String workorderCode,
                         String productCode, String productName, String warehouseId, String warehouseName,
                         String productLotCode, String recordType,
                         String quantityDelta, String referenceReceiptId, String operatorUserId, String operatorName,
                         String recordedBy,
                         String recordedByName, LocalDateTime occurredAt, LocalDateTime createTime, String reason) {
    }

    public record Detail(Ledger record, String originalBalance) {
    }

    public record Page(List<Ledger> items, long total, int offset, int limit) {
    }

    @GetMapping
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED, permission = LIST)
    public Page page(@RequestParam(required = false) Long workorderId, @RequestParam(required = false) Long executionId,
                     @RequestParam(required = false) Long warehouseId,
                     @RequestParam(required = false) String productLotCode,
                     @RequestParam(required = false) String referenceReceiptId,
                     @RequestParam(defaultValue = "0") int offset, @RequestParam(defaultValue = "20") int limit) {
        var p = service.page(new Query(workorderId, executionId, warehouseId, productLotCode, referenceReceiptId, offset, limit));
        return BeanConversions.convertPage(p, r -> view(r, p.userNames()), Page::new);
    }

    @GetMapping("/{id}")
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED, permission = DETAIL)
    public Detail detail(@PathVariable String id) {
        var d = service.detail(id);
        return new Detail(view(d.record(), d.userNames()), decimal(d.originalBalance()));
    }

    private static Ledger view(MesProductionReceiptEntity r, Map<Long, String> names) {
        ProductionReceiptRow ref = r instanceof ProductionReceiptRow row ? row : null;
        return new Ledger(r.getId(), r.getReceiptNo(), id(r.getExecutionId()), Optional.ofNullable(ref)
                .map(nullableValue -> nullableValue.getExecutionNo()).orElse(null), Optional.ofNullable(ref)
                .map(nullableValue -> nullableValue.getWorkorderCode()).orElse(null),
                Optional.ofNullable(ref).map(nullableValue -> nullableValue.getProductCode())
                        .orElse(null), Optional.ofNullable(ref).map(nullableValue -> nullableValue.getProductName())
                .orElse(null), id(r.getWarehouseId()), Optional.ofNullable(ref)
                .map(nullableValue -> nullableValue.getWarehouseName())
                .orElse(null), r.getProductLotCode(), r.getRecordType(),
                decimal(r.getQuantityDelta()), r.getReferenceReceiptId(), id(r.getOperatorUserId()), names.get(r.getOperatorUserId()), id(r.getRecordedBy()), names.get(r.getRecordedBy()), r.getOccurredAt(), r.getCreateTime(), r.getReason());
    }

    private static String id(Long v) {
        return Objects.toString(v, null);
    }

    private static String decimal(BigDecimal v) {
        if (v == null) return null;
        return v.toPlainString();
    }
}
