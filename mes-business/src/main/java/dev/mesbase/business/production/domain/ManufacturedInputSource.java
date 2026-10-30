package dev.mesbase.business.production.domain;

import java.math.BigDecimal;

/**
 * A formal receipt and its current stock identity. Stock availability is shared by all receipts
 * into that stock; it is not a remaining allocation for this particular receipt line.
 */
public record ManufacturedInputSource(Long documentId, String documentNo, Long lineId, String stockActionId,
                                      Long executionId, Long workorderId, Long bomId, Long stockId,
                                      Long materialId, Long warehouseId, Long unitId, String batchNo,
                                      String binCode, BigDecimal receivedQty, BigDecimal stockAvailableQty) {
}
