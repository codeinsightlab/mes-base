package dev.mesbase.business.production.controller;

import dev.mesbase.business.production.domain.ProductionReceiptModels.Receive;
import dev.mesbase.business.production.domain.ProductionReceiptModels.Reverse;
import dev.mesbase.business.production.domain.ProductionReceiptModels.Query;
import dev.mesbase.business.production.domain.MesProductionReceiptEntity;
import dev.mesbase.business.production.domain.ProductionReceiptRow;
import dev.mesbase.business.production.service.ProductionReceiptService;
import dev.mesbase.platform.web.EndpointAccess;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;

@RestController
@RequestMapping("/api/production/receipts")
@ConditionalOnProperty(name = "mes.business.enabled", havingValue = "true")
public class ProductionReceiptController {
    public static final String LIST = "business:production:receipt:list";
    public static final String DETAIL = "business:production:receipt:detail";
    public static final String CREATE = "business:production:receipt:create";
    public static final String REVERSE = "business:production:receipt:reverse";
    private final ProductionReceiptService service;

    public ProductionReceiptController(ProductionReceiptService service) {
        this.service = service;
    }

    public record ReceiveRequest(@NotNull Long executionId, @NotNull Long warehouseId,
                                 @Size(max = 100) String productLotCode,
                                 @NotNull @DecimalMin(value = "0", inclusive = false) @Digits(integer = 14, fraction = 6) BigDecimal quantity,
                                 @NotNull @Pattern(regexp = "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}") String requestId) {
    }

    public record ReverseRequest(
            @NotNull @DecimalMin(value = "0", inclusive = false) @Digits(integer = 14, fraction = 6) BigDecimal quantity,
            @NotBlank @Size(max = 500) String reason,
            @NotNull @Pattern(regexp = "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}") String requestId) {
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

    public record Summary(String executionId, String executionNo, String workorderId, String workorderCode,
                          String productId, String productCode, String productName, String targetQty, String status,
                          String routingLabel,
                          String pathStatus, String terminalNodeId, String terminalNodeName, String output,
                          String received, String remaining, String excess,
                          boolean canReceive, String blockedCode, String blockedReason, String released,
                          String releaseRemaining, String accepted, String pendingAcceptance, String releaseExcess) {
    }

    @GetMapping
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED, permission = LIST)
    public Page page(@RequestParam(required = false) Long workorderId, @RequestParam(required = false) Long executionId,
                     @RequestParam(required = false) Long warehouseId,
                     @RequestParam(required = false) String productLotCode,
                     @RequestParam(required = false) String referenceReceiptId,
                     @RequestParam(defaultValue = "0") int offset, @RequestParam(defaultValue = "20") int limit) {
        var p = service.page(new Query(workorderId, executionId, warehouseId, productLotCode, referenceReceiptId, offset, limit));
        return new Page(p.items().stream().map(r -> view(r, p.userNames())).toList(), p.total(), p.offset(), p.limit());
    }

    @GetMapping("/summary")
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED, permission = LIST)
    public Summary summary(@RequestParam Long executionId) {
        var s = service.summary(executionId);
        var i = s.identity();
        return new Summary(id(i.executionId()), i.executionNo(), id(i.workorderId()), i.workorderCode(), id(i.productId()), i.productCode(), i.productName(),
                decimal(i.targetQty()), i.status(), s.routingLabel(), s.pathStatus(), id(s.terminalNodeId()), s.terminalNodeName(), decimal(s.output()), decimal(s.received()),
                decimal(s.remaining()), decimal(s.excess()), s.canReceive(), s.blockedCode(), s.blockedReason(), decimal(s.released()), decimal(s.releaseRemaining()), decimal(s.accepted()), decimal(s.pendingAcceptance()), decimal(s.releaseExcess()));
    }

    @GetMapping("/{id}")
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED, permission = DETAIL)
    public Detail detail(@PathVariable String id) {
        var d = service.detail(id);
        return new Detail(view(d.record(), d.userNames()), decimal(d.originalBalance()));
    }

    @PostMapping
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED, permission = CREATE)
    public Ledger receive(@Valid @RequestBody ReceiveRequest c) {
        return view(service.receive(new Receive(c.executionId(), c.warehouseId(), c.productLotCode(), c.quantity(), c.requestId())), Map.of());
    }

    @PostMapping("/{id}/reverse")
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED, permission = REVERSE)
    public Ledger reverse(@PathVariable String id, @Valid @RequestBody ReverseRequest c) {
        return view(service.reverse(id, new Reverse(c.quantity(), c.reason(), c.requestId())), Map.of());
    }

    private static Ledger view(MesProductionReceiptEntity r, Map<Long, String> names) {
        ProductionReceiptRow ref = r instanceof ProductionReceiptRow row ? row : null;
        return new Ledger(r.getId(), r.getReceiptNo(), id(r.getExecutionId()), ref == null ? null : ref.getExecutionNo(), ref == null ? null : ref.getWorkorderCode(),
                ref == null ? null : ref.getProductCode(), ref == null ? null : ref.getProductName(), id(r.getWarehouseId()), ref == null ? null : ref.getWarehouseName(), r.getProductLotCode(), r.getRecordType(),
                decimal(r.getQuantityDelta()), r.getReferenceReceiptId(), id(r.getOperatorUserId()), names.get(r.getOperatorUserId()), id(r.getRecordedBy()), names.get(r.getRecordedBy()), r.getOccurredAt(), r.getCreateTime(), r.getReason());
    }

    private static String id(Long v) {
        return v == null ? null : v.toString();
    }

    private static String decimal(BigDecimal v) {
        return v == null ? null : v.toPlainString();
    }
}
