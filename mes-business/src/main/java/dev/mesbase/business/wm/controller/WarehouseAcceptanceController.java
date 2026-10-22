package dev.mesbase.business.wm.controller;

import dev.mesbase.common.utils.bean.BeanConversions;
import dev.mesbase.business.wm.service.WarehouseAcceptanceService;
import dev.mesbase.business.wm.domain.MesWarehouseAcceptanceEntity;
import dev.mesbase.platform.web.EndpointAccess;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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

    public record Page(List<Fact> items, long total, int offset, int limit) {
    }

    @GetMapping("/receipts/{id}/history")
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED, permission = "business:wm:acceptance:read")
    public Page history(@PathVariable String id, @RequestParam(defaultValue = "0") int offset,
                        @RequestParam(defaultValue = "20") int limit) {
        return BeanConversions.convertPage(service.history(id, offset, limit), Page.class);
    }

    private static Fact view(MesWarehouseAcceptanceEntity r) {
        return BeanConversions.convert(r, Fact.class);
    }
}
