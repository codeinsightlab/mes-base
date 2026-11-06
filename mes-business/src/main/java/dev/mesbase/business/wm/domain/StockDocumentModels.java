package dev.mesbase.business.wm.domain;

import dev.mesbase.common.core.page.PageData;
import dev.mesbase.core.error.ErrorCode;
import dev.mesbase.core.error.FoundationException;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Intent-only contracts; persisted status, identity, actors and action links are never client writable.
 */
public final class StockDocumentModels {
    private StockDocumentModels() {
    }

    private static String directionForType(String value) {
        var type = StockDocumentType.from(value);
        if (type == null) throw new FoundationException(ErrorCode.BAD_REQUEST);
        return type.getDirection().getValue();
    }

    public record Create(String direction, @NotBlank String documentType, @NotBlank @Size(max = 500) String reason,
                         @Size(max = 500) String remark, Long sourceWorkorderId, String sourceExecutionCode,
                         @Size(max = 64) String ruleCode, @Size(max = 200) String supplierName,
                         @Size(max = 128) String supplierDocumentNo, Long supplierId, LocalDate businessDate,
                         String currency) {
        public Create(String direction, String documentType, String reason, String remark, Long sourceWorkorderId,
                      String sourceExecutionCode, String ruleCode, String supplierName, String supplierDocumentNo) {
            this(direction, documentType, reason, remark, sourceWorkorderId, sourceExecutionCode, ruleCode,
                    supplierName, supplierDocumentNo, null, null, null);
        }

        public Create(String direction, String documentType, String reason, String remark, Long sourceWorkorderId,
                      String sourceExecutionCode, String ruleCode) {
            this(direction, documentType, reason, remark, sourceWorkorderId, sourceExecutionCode, ruleCode, null, null);
        }

        public Create(String documentType, String reason, String remark, Long sourceWorkorderId,
                      String sourceExecutionCode, String ruleCode) {
            this(directionForType(documentType),
                    documentType, reason, remark, sourceWorkorderId, sourceExecutionCode, ruleCode);
        }

        public Create(String documentType, String reason, String remark, Long sourceWorkorderId,
                      String sourceExecutionCode) {
            this(documentType, reason, remark, sourceWorkorderId, sourceExecutionCode, null);
        }
    }

    public record AggregateHeader(String direction, @NotBlank String documentType,
                                  @NotBlank @Size(max = 500) String reason,
                                  @Size(max = 500) String remark, Long sourceWorkorderId,
                                  String sourceExecutionCode, @Size(max = 64) String ruleCode,
                                  Long expectedRevision, @Size(max = 200) String supplierName,
                                  @Size(max = 128) String supplierDocumentNo, Long supplierId, LocalDate businessDate,
                                  String currency) {
        public AggregateHeader(String direction, String documentType, String reason, String remark,
                               Long sourceWorkorderId, String sourceExecutionCode, String ruleCode,
                               Long expectedRevision, String supplierName, String supplierDocumentNo) {
            this(direction, documentType, reason, remark, sourceWorkorderId, sourceExecutionCode, ruleCode,
                    expectedRevision, supplierName, supplierDocumentNo, null, null, null);
        }

        public AggregateHeader(String direction, String documentType, String reason, String remark,
                               Long sourceWorkorderId, String sourceExecutionCode, String ruleCode,
                               Long expectedRevision) {
            this(direction, documentType, reason, remark, sourceWorkorderId, sourceExecutionCode, ruleCode, expectedRevision, null, null);
        }

        public AggregateHeader(String documentType, String reason, String remark, Long sourceWorkorderId,
                               String sourceExecutionCode, String ruleCode, Long expectedRevision) {
            this(directionForType(documentType),
                    documentType, reason, remark, sourceWorkorderId, sourceExecutionCode, ruleCode, expectedRevision);
        }
    }

    public record AggregateLine(Long id, @NotNull Long stockId, @NotNull Long materialId,
                                @NotNull Long warehouseId, @NotNull Long unitId,
                                @Size(max = 128) String batchNo, @Size(max = 64) String binCode,
                                @NotNull @Positive @Digits(integer = 14, fraction = 6) BigDecimal quantity,
                                @Size(max = 500) String remark, BigDecimal unitPrice) {
        public AggregateLine(Long id, Long stockId, Long materialId, Long warehouseId, Long unitId,
                             String batchNo, String binCode, BigDecimal quantity, String remark) {
            this(id, stockId, materialId, warehouseId, unitId, batchNo, binCode, quantity, remark, null);
        }
    }

    public record ProductionIssueIntent(@NotBlank @Size(max = 36) String requestId,
                                        @NotNull @Positive @Digits(integer = 14, fraction = 6) BigDecimal outputQty,
                                        @NotNull Long routingId,
                                        @NotNull @Size(min = 1, max = 100) List<@NotNull Long> routingNodeIds,
                                        @NotBlank @Size(max = 24) String inputKind,
                                        @NotBlank @Size(max = 64) String calculationKey) {
    }

    public record AggregateSave(@NotNull AggregateHeader header,
                                @NotNull @Size(min = 1, max = 100) List<@NotNull @Valid AggregateLine> items,
                                @Valid ProductionIssueIntent productionIssue) {
        public AggregateSave(AggregateHeader header, List<AggregateLine> items) {
            this(header, items, null);
        }
    }

    public record Edit(@NotNull Long expectedRevision, @NotBlank @Size(max = 500) String reason,
                       @Size(max = 500) String remark, @Size(max = 200) String supplierName,
                       @Size(max = 128) String supplierDocumentNo, Long supplierId, LocalDate businessDate,
                       String currency) {
        public Edit(Long expectedRevision, String reason, String remark, String supplierName,
                    String supplierDocumentNo) {
            this(expectedRevision, reason, remark, supplierName, supplierDocumentNo, null, null, null);
        }

        public Edit(Long expectedRevision, String reason, String remark) {
            this(expectedRevision, reason, remark, null, null);
        }
    }

    public record Line(@NotNull Long expectedRevision, @NotNull Long stockId,
                       @NotNull Long materialId, @NotNull Long warehouseId, @NotNull Long unitId,
                       @Size(max = 128) String batchNo, @Size(max = 64) String binCode,
                       @NotNull @Positive @Digits(integer = 14, fraction = 6) BigDecimal quantity,
                       @Size(max = 500) String remark, BigDecimal unitPrice) {
        public Line(Long expectedRevision, Long stockId, Long materialId, Long warehouseId, Long unitId,
                    String batchNo, String binCode, BigDecimal quantity, String remark) {
            this(expectedRevision, stockId, materialId, warehouseId, unitId, batchNo, binCode, quantity, remark, null);
        }
    }

    public record Version(@NotNull Long expectedRevision) {
    }

    public record Post(@NotBlank String requestId, @NotNull Long expectedRevision) {
    }

    public record Query(String direction, String documentType, String status, String documentNo, Integer offset,
                        Integer limit) {
        public Query(String documentType, String status, String documentNo, Integer offset, Integer limit) {
            this(null, documentType, status, documentNo, offset, limit);
        }
    }

    public record Page(List<WmStockDocumentEntity> items, long total, int offset,
                       int limit) implements PageData<WmStockDocumentEntity> {
    }

    public record QualitySource(String sourceDeclarationId, String sourceTreatmentId,
                                String sourceQualityExecutionCode) {
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
    public record ProductionBasis(String executionCode, Long productId, BigDecimal effective,
                                  BigDecimal received, BigDecimal remaining, boolean canReceive, String blockedReason,
                                  boolean reportingRequired) {
    }

    public record CancellationBasis(Long workorderId, Long productId, String status,
                                    BigDecimal handedOver, BigDecimal accepted, BigDecimal manuallyReceived,
                                    BigDecimal remaining) {
    }
}
