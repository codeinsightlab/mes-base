// MES-CODEGEN: scaffold; review required. RuoYi template pipeline adapted under MIT.
package dev.mesbase.modules.material.controller;

import dev.mesbase.modules.material.sync.UnitMeasureSyncResult;
import dev.mesbase.modules.material.sync.UnitMeasureSyncService;
import dev.mesbase.platform.execution.FactoryContext;
import dev.mesbase.platform.web.EndpointAccess;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@ConditionalOnProperty(name = "mes.business.enabled", havingValue = "true")
public class UnitMeasureSyncController {
    private final UnitMeasureSyncService service;

    public UnitMeasureSyncController(UnitMeasureSyncService service) {
        this.service = service;
    }

    @PostMapping("/api/material/unit-measure/actions/sync")
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED,
            permission = UnitMeasurePermissions.SYNC)
    public UnitMeasureSyncResult sync() {
        return service.sync(FactoryContext.required());
    }
}

