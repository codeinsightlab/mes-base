// MES-CODEGEN: scaffold; review required. RuoYi template pipeline adapted under MIT.
package dev.mesbase.modules.md.controller;

import dev.mesbase.modules.md.sync.MdWorkshopSyncResult;
import dev.mesbase.modules.md.sync.MdWorkshopSyncService;
import dev.mesbase.platform.execution.FactoryContext;
import dev.mesbase.platform.web.EndpointAccess;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@ConditionalOnProperty(name = "mes.business.enabled", havingValue = "true")
public class MdWorkshopSyncController {
    private final MdWorkshopSyncService service;

    public MdWorkshopSyncController(MdWorkshopSyncService service) {
        this.service = service;
    }

    @PostMapping("/api/md/md-workshop/actions/sync")
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED,
            permission = MdWorkshopPermissions.SYNC)
    public MdWorkshopSyncResult sync() {
        return service.sync(FactoryContext.required());
    }
}

