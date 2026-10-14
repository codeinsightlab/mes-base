package dev.mesbase.business.integration.warehouse;

import dev.mesbase.core.factory.FactoryScope;
import dev.mesbase.core.error.FoundationException;
import dev.mesbase.core.error.ErrorCode;

/**
 * Warehouse ERP domain contract, not an HTTP adapter per API. Vendor implementations own internal workflows.
 */
public interface WarehouseErpAdapter {
    default ErpActionResult createMaterialIssue(FactoryScope scope, MaterialIssueCreateCommand command) {
        throw new FoundationException(ErrorCode.INTEGRATION_NOT_CONFIGURED);
    }

    default ErpIssueQueryResult queryMaterialIssue(FactoryScope scope, QueryMaterialIssueCommand command) {
        throw new FoundationException(ErrorCode.INTEGRATION_NOT_CONFIGURED);
    }

    boolean supports(FactoryScope scope, String bindingKey);

    ErpActionResult cancelMaterialIssue(FactoryScope scope, MaterialIssueCancelCommand command);

    ErpActionResult queryMaterialIssueCancellation(FactoryScope scope, MaterialIssueCancelCommand command);
}
