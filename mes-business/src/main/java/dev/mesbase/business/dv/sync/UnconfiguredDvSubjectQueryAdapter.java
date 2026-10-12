// MES-CODEGEN: scaffold; review required.
package dev.mesbase.business.dv.sync;

import dev.mesbase.core.error.ErrorCode;
import dev.mesbase.core.error.FoundationException;
import dev.mesbase.core.factory.FactoryScope;
import java.util.List;

/**
 * Default fallback. Register exactly one DvSubjectQueryPort bean when integrating an external source.
 */
public class UnconfiguredDvSubjectQueryAdapter implements DvSubjectQueryPort {
    @Override
    public List<ExternalDvSubject> query(FactoryScope scope, int maximum) {
        throw new FoundationException(ErrorCode.ERP_SYNC_NOT_CONFIGURED);
    }
}

