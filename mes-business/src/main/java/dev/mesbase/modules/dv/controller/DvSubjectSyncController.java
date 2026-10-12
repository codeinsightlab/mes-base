// MES-CODEGEN: scaffold; review required. RuoYi template pipeline adapted under MIT.
package dev.mesbase.modules.dv.controller;

import dev.mesbase.modules.dv.sync.DvSubjectSyncResult;
import dev.mesbase.modules.dv.sync.DvSubjectSyncService;
import dev.mesbase.platform.execution.FactoryContext;
import dev.mesbase.platform.web.EndpointAccess;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@ConditionalOnProperty(name = "mes.business.enabled", havingValue = "true")
public class DvSubjectSyncController {
    private final DvSubjectSyncService service;

    public DvSubjectSyncController(DvSubjectSyncService service) {
        this.service = service;
    }

    @PostMapping("/api/dv/dv-subject/actions/sync")
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED,
            permission = DvSubjectPermissions.SYNC)
    public DvSubjectSyncResult sync() {
        return service.sync(FactoryContext.required());
    }
}

