package dev.mesbase.business.integration.production;

import dev.mesbase.core.factory.FactoryScope;
import dev.mesbase.business.integration.production.ProductionSupply.Context;

/**
 * Optional provider: validate current ERP owner, release status, work center and stock scope server-side.
 */
public interface ProductionErpAdapter {
    boolean supports(FactoryScope scope, String bindingKey);

    Context query(FactoryScope scope, String bindingKey, String materialCode, String lotCode);
}
