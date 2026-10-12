// MES-CODEGEN: scaffold; review required. RuoYi template pipeline adapted under MIT.
package dev.mesbase.modules.qc.sync;

import dev.mesbase.core.error.ErrorCode;
import dev.mesbase.core.error.FoundationException;
import dev.mesbase.core.factory.FactoryScope;
import java.util.List;

/**
 * Default fallback. Register exactly one QcDefectQueryPort bean when integrating an external source.
 */
public class UnconfiguredQcDefectQueryAdapter implements QcDefectQueryPort {
    @Override
    public List<ExternalQcDefect> query(FactoryScope scope, int maximum) {
        throw new FoundationException(ErrorCode.SYNC_QUERY_NOT_IMPLEMENTED);
    }
}

