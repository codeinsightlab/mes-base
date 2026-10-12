// MES-CODEGEN: scaffold; review required. RuoYi template pipeline adapted under MIT.
package dev.mesbase.modules.material.sync;

import dev.mesbase.core.factory.FactoryScope;
import java.util.List;

/**
 * External query contract; map source data to schema-derived typed input. No persistence access here.
 */
public interface SafetyStockQueryPort {
    List<ExternalSafetyStock> query(FactoryScope scope, int maximum);
}

