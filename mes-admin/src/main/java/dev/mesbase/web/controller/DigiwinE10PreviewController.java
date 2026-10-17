package dev.mesbase.web.controller;

import dev.mesbase.platform.web.EndpointAccess;
import dev.mesbase.server.integration.e10.DigiwinE10SyncPreview;
import dev.mesbase.server.integration.e10.DigiwinE10SyncPreview.Kind;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 只返回安全候选字段，不暴露 SDK 响应、请求身份或连接配置。
 */
@RestController
@ConditionalOnProperty(name = "mes.runtime.enabled", havingValue = "true")
public class DigiwinE10PreviewController {
    private final DigiwinE10SyncPreview service;

    public DigiwinE10PreviewController(DigiwinE10SyncPreview service) {
        this.service = service;
    }

    @GetMapping("/api/production/workorder/erp-preview")
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED, permission = "workorder:sync")
    public DigiwinE10SyncPreview.Capability workorderCapability() {
        return service.capability(Kind.WORKORDER);
    }

    @PostMapping("/api/production/workorder/erp-preview")
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED, permission = "workorder:sync")
    public DigiwinE10SyncPreview.Preview workorders(@RequestParam(defaultValue = "1") int pageNo,
                                                    @RequestParam(defaultValue = "20") int pageSize) {
        return service.preview(Kind.WORKORDER, pageNo, pageSize);
    }

    @GetMapping("/api/wm/wm-warehouse/erp-preview")
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED, permission = "business:wm:warehouse:sync")
    public DigiwinE10SyncPreview.Capability warehouseCapability() {
        return service.capability(Kind.WAREHOUSE);
    }

    @PostMapping("/api/wm/wm-warehouse/erp-preview")
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED, permission = "business:wm:warehouse:sync")
    public DigiwinE10SyncPreview.Preview warehouses(@RequestParam(defaultValue = "1") int pageNo,
                                                    @RequestParam(defaultValue = "20") int pageSize) {
        return service.preview(Kind.WAREHOUSE, pageNo, pageSize);
    }
}
