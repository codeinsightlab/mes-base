package dev.mesbase.business.production.controller;

import dev.mesbase.business.production.domain.WorkorderMaterialModels;
import dev.mesbase.business.production.service.WorkorderMaterialService;
import dev.mesbase.platform.web.EndpointAccess;
import dev.mesbase.common.utils.bean.BeanConversions;
import java.time.LocalDateTime;
import java.util.List;
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

    public record View(String workorderId, String bomId, String bomRevision, String revision,
                       String warehouseIds, String workCenter, String status, LocalDateTime frozenAt,
                       LocalDateTime updatedAt, List<ItemView> items) {
    }

    public record ItemView(String id, String bomItemId, String materialId, String unitId,
                           String totalRequiredQty, String standardRequiredQty, String bomBaseQty,
                           String bomComponentQty, String plannedQty, String outputUnitId, boolean manuallyAdjusted) {
    }

    @GetMapping
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED, permission = "workorder:query")
    public View get(@PathVariable Long id) {
        return view(service.get(id));
    }

    @PutMapping
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED, permission = "workorder:material:update")
    public View save(@PathVariable Long id, @RequestBody WorkorderMaterialModels.Save c) {
        return view(service.save(id, c));
    }

    private static View view(WorkorderMaterialModels.Document r) {
        return BeanConversions.convert(r, View.class);
    }
}
