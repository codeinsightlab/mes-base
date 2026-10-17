package dev.mesbase.business.wm.domain;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;

/**
 * Intent-only contracts; persisted status, identity, actors and action links are never client writable.
 */
public final class StockDocumentModels {
    private StockDocumentModels() {
    }

    public record Create(@NotBlank String documentType, @NotBlank @Size(max = 500) String reason,
                         @Size(max = 500) String remark, Long sourceWorkorderId) {
    }

    public record Edit(@NotNull Long expectedRevision, @NotBlank @Size(max = 500) String reason,
                       @Size(max = 500) String remark) {
    }

    public record Line(@NotNull Long expectedRevision, @NotNull Long stockId,
                       @NotNull Long materialId, @NotNull Long warehouseId, @NotNull Long unitId,
                       @Size(max = 128) String batchNo, @Size(max = 64) String binCode,
                       @NotNull @Positive @Digits(integer = 14, fraction = 6) BigDecimal quantity,
                       @Size(max = 500) String remark) {
    }

    public record Version(@NotNull Long expectedRevision) {
    }

    public record Post(@NotBlank String requestId, @NotNull Long expectedRevision) {
    }

    public record Query(String documentType, String status, String documentNo, Integer offset, Integer limit) {
    }

    public record Page(List<WmStockDocumentEntity> items, long total, int offset, int limit) {
    }

    public record Detail(WmStockDocumentEntity document, List<WmStockDocumentLineEntity> lines) {
    }

    /**
     * Only net formal handover not already received is eligible; never infer stock from plans/reports.
     */
    public record CancellationBasis(Long workorderId, Long productId, String status,
                                    BigDecimal handedOver, BigDecimal accepted, BigDecimal manuallyReceived,
                                    BigDecimal remaining) {
    }
}
