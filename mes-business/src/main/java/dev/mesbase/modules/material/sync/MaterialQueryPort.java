// MES-CODEGEN: scaffold; review required.
package dev.mesbase.modules.material.sync;

import dev.mesbase.core.factory.FactoryScope;
import java.util.List;

/**
 * External query contract; map source data to schema-derived typed input. No persistence access here.
 */
public interface MaterialQueryPort {
    List<ExternalMaterial> query(FactoryScope scope, int maximum);
}

