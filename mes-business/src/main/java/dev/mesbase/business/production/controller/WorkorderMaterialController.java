package dev.mesbase.business.production.controller;

import dev.mesbase.business.production.domain.MesWorkorderMaterialEntity;
import dev.mesbase.business.production.service.WorkorderMaterialService;
import dev.mesbase.platform.web.EndpointAccess;
import java.time.LocalDateTime;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/production/workorder/{id}/material")
@ConditionalOnProperty(name = "mes.business.enabled", havingValue = "true")
public class WorkorderMaterialController {
    private final WorkorderMaterialService service;

    public WorkorderMaterialController(WorkorderMaterialService service) {
        this.service = service;
    }

    public record View(String workorderId, String materialId, String unitId, String totalRequiredQty,
                       String plannedQty, String warehouseIds, String workCenter, String revision, String status,
                       LocalDateTime updatedAt) {
    }

    @GetMapping
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED, permission = "workorder:query")
    public View get(@PathVariable Long id) {
        return view(service.get(id));
    }

    @PutMapping
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED, permission = "workorder:material:update")
    public View save(@PathVariable Long id, @RequestBody WorkorderMaterialService.Save c) {
        return view(service.save(id, c));
    }

    private static View view(MesWorkorderMaterialEntity r) {
        if (r == null) return null;
        return new View(r.getId().toString(), r.getMaterialId().toString(), r.getUnitId()
                .toString(), r.getTotalRequiredQty().toPlainString(), r.getPlannedQty()
                .toPlainString(), r.getWarehouseIds(), r.getWorkCenter(), r.getRevision()
                .toString(), r.getStatus(), r.getUpdatedAt());
    }
}
