package dev.mesbase.business.integration.warehouse;

import dev.mesbase.core.factory.FactoryScope;

/**
 * Default domain implementation: local completion never invents external facts.
 */
public final class LocalWarehouseErpService implements WarehouseErpAdapter {
    @Override
    public boolean supports(FactoryScope scope, String bindingKey) {
        return "local".equals(bindingKey);
    }

    @Override
    public ErpActionResult createMaterialIssue(FactoryScope scope, MaterialIssueSubmitCommand command) {
        return ErpActionResult.localSuccess();
    }

    @Override
    public ErpActionResult cancelMaterialIssue(FactoryScope scope, MaterialIssueCancelCommand command) {
        return ErpActionResult.localSuccess();
    }

    @Override
    public ErpActionResult queryMaterialIssueCancellation(FactoryScope scope, MaterialIssueCancelCommand command) {
        return ErpActionResult.localSuccess();
    }

    @Override
    public ErpIssueQueryResult queryMaterialIssue(FactoryScope scope, QueryMaterialIssueCommand command) {
        return ErpIssueQueryResult.local();
    }
}
