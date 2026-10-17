package dev.mesbase.web.controller;

import dev.mesbase.platform.web.EndpointAccess;
import dev.mesbase.server.integration.e10.DigiwinE10SyncPreview;
import dev.mesbase.server.integration.e10.DigiwinE10SyncService;
import dev.mesbase.core.error.ErrorCode;
import dev.mesbase.core.error.FoundationException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
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
    private DigiwinE10SyncService workflow;

    public DigiwinE10PreviewController(DigiwinE10SyncPreview service) {
        this.service = service;
    }

    @Autowired
    public DigiwinE10PreviewController(DigiwinE10SyncPreview service, ObjectProvider<DigiwinE10SyncService> workflow) {
        this.service = service;
        this.workflow = workflow.getIfAvailable();
    }

    private DigiwinE10SyncService workflow() {
        if (workflow == null) throw new FoundationException(ErrorCode.INTEGRATION_NOT_CONFIGURED);
        return workflow;
    }

    @GetMapping("/api/production/workorder/erp-preview")
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED, permission = "workorder:sync")
    public DigiwinE10SyncPreview.Capability workorderCapability() {
        return workflow == null ? service.capability(Kind.WORKORDER) : workflow.capability(Kind.WORKORDER);
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
        return workflow == null ? service.capability(Kind.WAREHOUSE) : workflow.capability(Kind.WAREHOUSE);
    }

    @PostMapping("/api/wm/wm-warehouse/erp-preview")
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED, permission = "business:wm:warehouse:sync")
    public DigiwinE10SyncPreview.Preview warehouses(@RequestParam(defaultValue = "1") int pageNo,
                                                    @RequestParam(defaultValue = "20") int pageSize) {
        return service.preview(Kind.WAREHOUSE, pageNo, pageSize);
    }

    @PostMapping("/api/production/workorder/erp-preview/dry-run")
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED, permission = "workorder:sync")
    public DigiwinE10SyncService.Result workorderDryRun(@RequestParam(defaultValue = "20") int pageSize,
                                                        @RequestParam(defaultValue = "5") int maxPages) {
        return workflow().execute(Kind.WORKORDER, false, pageSize, maxPages);
    }

    @PostMapping("/api/production/workorder/erp-preview/commit")
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED, permission = "workorder:sync")
    public DigiwinE10SyncService.Result workorderCommit(@RequestParam(defaultValue = "20") int pageSize,
                                                        @RequestParam(defaultValue = "5") int maxPages) {
        return workflow().execute(Kind.WORKORDER, true, pageSize, maxPages);
    }

    @PostMapping("/api/wm/wm-warehouse/erp-preview/dry-run")
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED, permission = "business:wm:warehouse:sync")
    public DigiwinE10SyncService.Result warehouseDryRun(@RequestParam(defaultValue = "20") int pageSize,
                                                        @RequestParam(defaultValue = "5") int maxPages) {
        return workflow().execute(Kind.WAREHOUSE, false, pageSize, maxPages);
    }

    @PostMapping("/api/wm/wm-warehouse/erp-preview/commit")
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED, permission = "business:wm:warehouse:sync")
    public DigiwinE10SyncService.Result warehouseCommit(@RequestParam(defaultValue = "20") int pageSize,
                                                        @RequestParam(defaultValue = "5") int maxPages) {
        return workflow().execute(Kind.WAREHOUSE, true, pageSize, maxPages);
    }
}
