package dev.mesbase.business.production.controller;

import dev.mesbase.business.production.service.ManufacturedInputSourceService;
import dev.mesbase.common.utils.bean.BeanConversions;
import dev.mesbase.platform.web.EndpointAccess;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/production/input/manufactured-sources")
@ConditionalOnProperty(name = "mes.business.enabled", havingValue = "true")
public class ManufacturedInputSourceController {
    private final ManufacturedInputSourceService service;

    public ManufacturedInputSourceController(ManufacturedInputSourceService service) {
        this.service = service;
    }

    public record Source(String documentId, String documentNo, String lineId, String stockActionId,
                         String executionId, String workorderId, String bomId, String stockId,
                         String materialId, String warehouseId, String unitId, String batchNo,
                         String binCode, String receivedQty, String stockAvailableQty) {
    }

    public record Page(List<Source> items, long total, int offset, int limit) {
    }

    @GetMapping
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED, permission = ProductionInputController.PERMISSION)
    public Page query(@RequestParam Long workorderId, @RequestParam(defaultValue = "0") int offset,
                      @RequestParam(defaultValue = "20") int limit) {
        return BeanConversions.convertPage(service.query(workorderId, offset, limit), Page.class);
    }
}
