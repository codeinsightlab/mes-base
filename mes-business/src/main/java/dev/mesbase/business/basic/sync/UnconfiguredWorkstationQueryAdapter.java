// MES-CODEGEN: scaffold; review required.
package dev.mesbase.business.basic.sync;

import dev.mesbase.core.error.ErrorCode;
import dev.mesbase.core.error.FoundationException;
import dev.mesbase.core.factory.FactoryScope;
import java.util.List;

/**
 * Default fallback. Register exactly one WorkstationQueryPort bean when integrating an external source.
 */
public class UnconfiguredWorkstationQueryAdapter implements WorkstationQueryPort {
    @Override
    public List<ExternalWorkstation> query(FactoryScope scope, int maximum) {
        throw new FoundationException(ErrorCode.ERP_SYNC_NOT_CONFIGURED);
    }
}

