// MES-CODEGEN: scaffold; review required. RuoYi template pipeline adapted under MIT.
package dev.mesbase.modules.qc.controller;

import dev.mesbase.modules.qc.sync.QcIndexSyncResult;
import dev.mesbase.modules.qc.sync.QcIndexSyncService;
import dev.mesbase.platform.execution.FactoryContext;
import dev.mesbase.platform.web.EndpointAccess;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@ConditionalOnProperty(name = "mes.business.enabled", havingValue = "true")
public class QcIndexSyncController {
    private final QcIndexSyncService service;

    public QcIndexSyncController(QcIndexSyncService service) {
        this.service = service;
    }

    @PostMapping("/api/qc/qc-index/actions/sync")
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED,
            permission = QcIndexPermissions.SYNC)
    public QcIndexSyncResult sync() {
        return service.sync(FactoryContext.required());
    }
}

