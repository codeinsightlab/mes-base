// MES-CODEGEN: scaffold; review required. RuoYi template pipeline adapted under MIT.
package dev.mesbase.modules.dv.sync;

import dev.mesbase.core.error.ErrorCode;
import dev.mesbase.core.error.FoundationException;
import dev.mesbase.core.factory.FactoryScope;
import dev.mesbase.framework.sync.SyncCoordinator;
import dev.mesbase.platform.execution.FactoryContext;
import java.time.Duration;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "mes.business.enabled", havingValue = "true")
public class DvSubjectSyncService {
    private final DvSubjectQueryPort query;
    private final SyncCoordinator coordinator;
    private final DvSubjectSyncWriter writer;

    public DvSubjectSyncService(ObjectProvider<DvSubjectQueryPort> queries,
                                SyncCoordinator coordinator, DvSubjectSyncWriter writer) {
        this.query = queries.getIfAvailable(UnconfiguredDvSubjectQueryAdapter::new);
        this.coordinator = coordinator;
        this.writer = writer;
    }

    public DvSubjectSyncResult sync(FactoryScope scope) {
        if (!FactoryContext.required().equals(scope)) {
            throw new FoundationException(ErrorCode.CONFLICT);
        }
        var inputs = query.query(scope, 1000);
        var run = coordinator.claim(scope, "codegen.dv_subject", Duration.ofMinutes(5));
        try {
            coordinator.progress(run, 0);
            var result = writer.upsert(scope, inputs);
            coordinator.progress(run, result.fetched());
            coordinator.succeed(run, null);
            return result;
        } catch (RuntimeException failure) {
            try {
                coordinator.fail(run);
            } catch (RuntimeException deliveryFailure) {
                failure.addSuppressed(deliveryFailure);
            }
            throw failure;
        }
    }
}

