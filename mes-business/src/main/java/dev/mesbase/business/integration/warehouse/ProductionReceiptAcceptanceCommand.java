package dev.mesbase.business.integration.warehouse;

import java.math.BigDecimal;

/**
 * External warehouse contract only. LOCAL facts and balances never stand in for ERP execution.
 */
public record ProductionReceiptAcceptanceCommand(String requestId, String receiptId, Long executionId,
                                                 String productCode, String productLotCode, String warehouseCode,
                                                 BigDecimal quantity, String reason,
                                                 MaterialIssueSubmitCommand.Source source) {
}
