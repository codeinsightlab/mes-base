// MES-CODEGEN: scaffold; review required. RuoYi template pipeline adapted under MIT.
package dev.mesbase.modules.material.controller;

import dev.mesbase.modules.material.sync.SafetyStockSyncResult;
import dev.mesbase.modules.material.sync.SafetyStockSyncService;
import dev.mesbase.platform.execution.FactoryContext;
import dev.mesbase.platform.web.EndpointAccess;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@ConditionalOnProperty(name = "mes.business.enabled", havingValue = "true")
public class SafetyStockSyncController {
    private final SafetyStockSyncService service;

    public SafetyStockSyncController(SafetyStockSyncService service) {
        this.service = service;
    }

    @PostMapping("/api/material/safety-stock/actions/sync")
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED,
            permission = SafetyStockPermissions.SYNC)
    public SafetyStockSyncResult sync() {
        return service.sync(FactoryContext.required());
    }
}

