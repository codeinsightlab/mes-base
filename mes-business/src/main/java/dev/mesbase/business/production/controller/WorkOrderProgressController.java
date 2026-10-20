package dev.mesbase.business.production.controller;

import dev.mesbase.business.production.service.WorkOrderProgressQueryService;
import dev.mesbase.platform.web.EndpointAccess;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@ConditionalOnProperty(name = "mes.business.enabled", havingValue = "true")
public class WorkOrderProgressController {
    private final WorkOrderProgressQueryService service;

    public WorkOrderProgressController(WorkOrderProgressQueryService service) {
        this.service = service;
    }

    public record Row(String id, String code, String source, String status, String productCode, String productName,
                      String unitName, String plannedQty, String completedQty, String remainingQty,
                      String completionPercent, long completedExecutions, LocalDateTime lastCompletedAt, String issue) {
    }

    public record Page(List<Row> items, long total, int offset, int limit) {
    }

    @GetMapping("/api/production/workorder-progress")
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED, permission = "workorder:list")
    public Page page(@RequestParam(required = false) String keyword, @RequestParam(required = false) String status,
                     @RequestParam(defaultValue = "0") int offset, @RequestParam(defaultValue = "5") int limit) {
        var page = service.page(keyword, status, offset, limit);
        return new Page(page.items().stream().map(p -> new Row(p.order().id().toString(), p.order().code(),
                p.order().source(), p.order().status(), p.order().productCode(), p.order().productName(),
                p.order().unitName(), decimal(p.order().plannedQty()), decimal(p.completedQty()),
                decimal(p.remainingQty()), decimal(p.completionPercent()), p.completedExecutions(),
                p.lastCompletedAt(), p.issue())).toList(), page.total(), page.offset(), page.limit());
    }

    private static String decimal(BigDecimal value) {
        return value == null ? null : value.toPlainString();
    }
}
