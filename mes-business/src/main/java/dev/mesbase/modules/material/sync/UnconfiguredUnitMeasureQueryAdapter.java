// MES-CODEGEN: scaffold; review required.
package dev.mesbase.modules.material.sync;

import dev.mesbase.core.error.ErrorCode;
import dev.mesbase.core.error.FoundationException;
import dev.mesbase.core.factory.FactoryScope;
import java.util.List;

/**
 * Default fallback. Register exactly one UnitMeasureQueryPort bean when integrating an external source.
 */
public class UnconfiguredUnitMeasureQueryAdapter implements UnitMeasureQueryPort {
    @Override
    public List<ExternalUnitMeasure> query(FactoryScope scope, int maximum) {
        throw new FoundationException(ErrorCode.ERP_SYNC_NOT_CONFIGURED);
    }
}

