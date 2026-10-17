package dev.mesbase.business.integration.warehouse;

import dev.mesbase.core.factory.FactoryScope;
import dev.mesbase.core.error.FoundationException;
import dev.mesbase.core.error.ErrorCode;

/**
 * Warehouse ERP domain contract, not an HTTP adapter per API. Vendor implementations own internal workflows.
 */
public interface WarehouseErpAdapter {
    default ErpActionResult acceptProductionReceipt(FactoryScope scope, ProductionReceiptAcceptanceCommand command) {
        throw new FoundationException(ErrorCode.INTEGRATION_NOT_CONFIGURED);
    }

    default ErpActionResult createMaterialIssue(FactoryScope scope, MaterialIssueSubmitCommand command) {
        throw new FoundationException(ErrorCode.INTEGRATION_NOT_CONFIGURED);
    }

    default ErpIssueQueryResult queryMaterialIssue(FactoryScope scope, QueryMaterialIssueCommand command) {
        throw new FoundationException(ErrorCode.INTEGRATION_NOT_CONFIGURED);
    }

    boolean supports(FactoryScope scope, String bindingKey);

    /**
     * Historical dispatch uses frozen provider plus binding, never current Factory configuration.
     */
    default boolean supportsProvider(String provider) {
        return false;
    }

    ErpActionResult cancelMaterialIssue(FactoryScope scope, MaterialIssueCancelCommand command);

    ErpActionResult queryMaterialIssueCancellation(FactoryScope scope, MaterialIssueCancelCommand command);
}
