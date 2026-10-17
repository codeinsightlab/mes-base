package dev.mesbase.business.wm.controller;

import dev.mesbase.business.wm.service.WarehouseAcceptanceService;
import dev.mesbase.business.wm.domain.MesWarehouseAcceptanceEntity;
import dev.mesbase.platform.web.EndpointAccess;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

@RestController
@RequestMapping("/api/wm/production-acceptances")
@ConditionalOnProperty(name = "mes.business.enabled", havingValue = "true")
public class WarehouseAcceptanceController {
    private final WarehouseAcceptanceService service;

    public WarehouseAcceptanceController(WarehouseAcceptanceService service) {
        this.service = service;
    }

    public record Fact(String id, String receiptId, String executionId, String productId, String stockId,
                       String quantityDelta, String referenceId, String stockActionId, String operatorUserId,
                       LocalDateTime recordedAt, String reason) {
    }

    public record State(String receiptId, String executionId, String productId, String warehouseId,
                        String productLotCode, String netHandover, String accepted, String pending) {
    }

    public record Page(List<Fact> items, long total, int offset, int limit) {
    }

    @GetMapping("/receipts/{id}")
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED, permission = "business:wm:acceptance:read")
    public State state(@PathVariable String id) {
        var s = service.state(id);
        return new State(id, s.receipt().getExecutionId().toString(), s.productId().toString(), s.receipt()
                .getWarehouseId().toString(), s.receipt().getProductLotCode(), s.netHandover()
                .toPlainString(), s.accepted().toPlainString(), s.pending().toPlainString());
    }

    @GetMapping("/receipts/{id}/history")
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED, permission = "business:wm:acceptance:read")
    public Page history(@PathVariable String id, @RequestParam(defaultValue = "0") int offset,
                        @RequestParam(defaultValue = "20") int limit) {
        var p = service.history(id, offset, limit);
        return new Page(p.items().stream().map(WarehouseAcceptanceController::view).toList(), p.total(), offset, limit);
    }

    @PostMapping("/receipts/{id}")
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED, permission = "business:wm:acceptance:create")
    public Fact accept(@PathVariable String id, @RequestBody WarehouseAcceptanceService.Accept c) {
        return view(service.accept(id, c));
    }

    @PostMapping("/{id}/reverse")
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED, permission = "business:wm:acceptance:reverse")
    public Fact reverse(@PathVariable String id, @RequestBody WarehouseAcceptanceService.Reverse c) {
        return view(service.reverse(id, c));
    }

    private static Fact view(MesWarehouseAcceptanceEntity r) {
        return new Fact(r.getId(), r.getReceiptId(), r.getExecutionId().toString(), r.getProductId()
                .toString(), r.getStockId().toString(), r.getQuantityDelta()
                .toPlainString(), r.getReferenceId(), r.getStockActionId(), r.getOperatorUserId()
                .toString(), r.getRecordedAt(), r.getReason());
    }
}
