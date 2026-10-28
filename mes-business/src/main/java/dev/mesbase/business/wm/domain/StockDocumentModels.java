package dev.mesbase.business.wm.domain;

import dev.mesbase.common.core.page.PageData;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.util.List;

/**
 * Intent-only contracts; persisted status, identity, actors and action links are never client writable.
 */
public final class StockDocumentModels {
    private StockDocumentModels() {
    }

    public record Create(@NotBlank String documentType, @NotBlank @Size(max = 500) String reason,
                         @Size(max = 500) String remark, Long sourceWorkorderId, Long sourceExecutionId,
                         @Size(max = 64) String ruleCode) {
        public Create(String documentType, String reason, String remark, Long sourceWorkorderId,
                      Long sourceExecutionId) {
            this(documentType, reason, remark, sourceWorkorderId, sourceExecutionId, null);
        }
    }

    public record AggregateHeader(@NotBlank String documentType, @NotBlank @Size(max = 500) String reason,
                                  @Size(max = 500) String remark, Long sourceWorkorderId,
                                  Long sourceExecutionId, @Size(max = 64) String ruleCode,
                                  Long expectedRevision) {
    }

    public record AggregateLine(Long id, @NotNull Long stockId, @NotNull Long materialId,
                                @NotNull Long warehouseId, @NotNull Long unitId,
                                @Size(max = 128) String batchNo, @Size(max = 64) String binCode,
                                @NotNull @Positive @Digits(integer = 14, fraction = 6) BigDecimal quantity,
                                @Size(max = 500) String remark) {
    }

    public record AggregateSave(@NotNull AggregateHeader header,
                                @NotNull @Size(min = 1, max = 100) List<@NotNull @Valid AggregateLine> items) {
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

    public record Page(List<WmStockDocumentEntity> items, long total, int offset,
                       int limit) implements PageData<WmStockDocumentEntity> {
    }

    public record QualitySource(String sourceDeclarationId, String sourceTreatmentId, Long sourceQualityExecutionId) {
    }

    public record MaterialIssueBasis(String requestId, String code, Long workorderId, BigDecimal outputQty,
                                     Long routingId, List<Long> routingNodeIds) {
    }

    public record Detail(WmStockDocumentEntity document, List<WmStockDocumentLineEntity> lines,
                         QualitySource qualitySource, MaterialIssueBasis materialIssueBasis,
                         String materialIssueBasisError) {
    }

    /**
     * Current net production output less formally received stock bounds inbound approval.
     */
    public record ProductionBasis(Long executionId, Long productId, BigDecimal effective,
                                  BigDecimal received, BigDecimal remaining, boolean canReceive, String blockedReason) {
    }

    public record CancellationBasis(Long workorderId, Long productId, String status,
                                    BigDecimal handedOver, BigDecimal accepted, BigDecimal manuallyReceived,
                                    BigDecimal remaining) {
    }
}
