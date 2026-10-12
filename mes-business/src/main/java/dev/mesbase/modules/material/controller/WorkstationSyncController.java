// MES-CODEGEN: scaffold; review required. RuoYi template pipeline adapted under MIT.
package dev.mesbase.modules.material.controller;

import dev.mesbase.modules.material.sync.WorkstationSyncResult;
import dev.mesbase.modules.material.sync.WorkstationSyncService;
import dev.mesbase.platform.execution.FactoryContext;
import dev.mesbase.platform.web.EndpointAccess;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@ConditionalOnProperty(name = "mes.business.enabled", havingValue = "true")
public class WorkstationSyncController {
    private final WorkstationSyncService service;

    public WorkstationSyncController(WorkstationSyncService service) {
        this.service = service;
    }

    @PostMapping("/api/material/workstation/actions/sync")
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED,
            permission = WorkstationPermissions.SYNC)
    public WorkstationSyncResult sync() {
        return service.sync(FactoryContext.required());
    }
}

