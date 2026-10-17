package dev.mesbase.business.production.controller;

import dev.mesbase.business.production.domain.MesWorkorderCancellationEntity;
import dev.mesbase.business.production.service.WorkOrderCancellationService;
import dev.mesbase.business.production.service.WorkOrderCancellationService.Command;
import dev.mesbase.platform.web.EndpointAccess;
import java.time.LocalDateTime;
import dev.mesbase.business.production.domain.WorkOrderStatus;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/production/workorder/{id}")
@ConditionalOnProperty(name = "mes.business.enabled", havingValue = "true")
public class WorkOrderCancellationController {
    private final WorkOrderCancellationService service;

    public WorkOrderCancellationController(WorkOrderCancellationService service) {
        this.service = service;
    }

    public record View(String workOrderId, String status, String cancellationId, LocalDateTime cancelledAt,
                       String cancelledBy, String reason, String previousStatus, String sourceSnapshot) {
    }

    @PostMapping("/actions/cancel")
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED, permission = "production:workorder:cancel")
    public View cancel(@PathVariable Long id, @RequestBody Command command) {
        return view(service.cancel(id, command));
    }

    @GetMapping("/cancellation")
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED, permission = "workorder:query")
    public View history(@PathVariable Long id) {
        return view(service.history(id));
    }

    private static View view(MesWorkorderCancellationEntity fact) {
        return fact == null ? null : new View(fact.getWorkorderId()
                .toString(), WorkOrderStatus.CANCELLED.getValue(), fact.getId(), fact.getRecordedAt(),
                fact.getOperatorUserId()
                        .toString(), fact.getReason(), fact.getPreviousStatus(), fact.getSourceSnapshot());
    }
}
