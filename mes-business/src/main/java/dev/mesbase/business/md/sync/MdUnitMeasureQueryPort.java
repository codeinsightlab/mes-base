// MES-CODEGEN: scaffold; review required.
package dev.mesbase.business.md.sync;

import dev.mesbase.core.factory.FactoryScope;
import java.util.List;

/**
 * External query contract; map source data to schema-derived typed input. No persistence access here.
 */
public interface MdUnitMeasureQueryPort {
    List<ExternalMdUnitMeasure> query(FactoryScope scope, int maximum);
}

