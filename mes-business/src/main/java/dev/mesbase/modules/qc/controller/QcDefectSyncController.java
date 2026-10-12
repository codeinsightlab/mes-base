// MES-CODEGEN: scaffold; review required. RuoYi template pipeline adapted under MIT.
package dev.mesbase.modules.qc.controller;

import dev.mesbase.modules.qc.sync.QcDefectSyncResult;
import dev.mesbase.modules.qc.sync.QcDefectSyncService;
import dev.mesbase.platform.execution.FactoryContext;
import dev.mesbase.platform.web.EndpointAccess;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@ConditionalOnProperty(name = "mes.business.enabled", havingValue = "true")
public class QcDefectSyncController {
    private final QcDefectSyncService service;

    public QcDefectSyncController(QcDefectSyncService service) {
        this.service = service;
    }

    @PostMapping("/api/qc/qc-defect/actions/sync")
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED,
            permission = QcDefectPermissions.SYNC)
    public QcDefectSyncResult sync() {
        return service.sync(FactoryContext.required());
    }
}

