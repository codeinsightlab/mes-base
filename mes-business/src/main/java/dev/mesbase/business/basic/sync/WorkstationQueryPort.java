// MES-CODEGEN: scaffold; review required.
package dev.mesbase.business.basic.sync;

import dev.mesbase.core.factory.FactoryScope;
import java.util.List;

/** External query contract; map source data to schema-derived typed input. No persistence access here. */
public interface WorkstationQueryPort {
    List<ExternalWorkstation> query(FactoryScope scope, int maximum);
}

