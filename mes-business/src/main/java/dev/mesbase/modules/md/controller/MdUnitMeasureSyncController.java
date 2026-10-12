// MES-CODEGEN: scaffold; review required. RuoYi template pipeline adapted under MIT.
package dev.mesbase.modules.md.controller;

import dev.mesbase.modules.md.sync.MdUnitMeasureSyncResult;
import dev.mesbase.modules.md.sync.MdUnitMeasureSyncService;
import dev.mesbase.platform.execution.FactoryContext;
import dev.mesbase.platform.web.EndpointAccess;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@ConditionalOnProperty(name = "mes.business.enabled", havingValue = "true")
public class MdUnitMeasureSyncController {
    private final MdUnitMeasureSyncService service;

    public MdUnitMeasureSyncController(MdUnitMeasureSyncService service) {
        this.service = service;
    }

    @PostMapping("/api/md/md-unit-measure/actions/sync")
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED,
            permission = MdUnitMeasurePermissions.SYNC)
    public MdUnitMeasureSyncResult sync() {
        return service.sync(FactoryContext.required());
    }
}

