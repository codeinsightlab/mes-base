// MES-CODEGEN: scaffold; review required. RuoYi template pipeline adapted under MIT.
package dev.mesbase.modules.pro.controller;

import dev.mesbase.modules.pro.sync.ProWorkorderSyncResult;
import dev.mesbase.modules.pro.sync.ProWorkorderSyncService;
import dev.mesbase.platform.execution.FactoryContext;
import dev.mesbase.platform.web.EndpointAccess;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@ConditionalOnProperty(name = "mes.business.enabled", havingValue = "true")
public class ProWorkorderSyncController {
    private final ProWorkorderSyncService service;

    public ProWorkorderSyncController(ProWorkorderSyncService service) {
        this.service = service;
    }

    @PostMapping("/api/pro/pro-workorder/actions/sync")
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED,
            permission = ProWorkorderPermissions.SYNC)
    public ProWorkorderSyncResult sync() {
        return service.sync(FactoryContext.required());
    }
}

