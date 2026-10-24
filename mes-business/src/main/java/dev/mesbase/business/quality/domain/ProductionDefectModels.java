package dev.mesbase.business.quality.domain;

import dev.mesbase.business.quality.domain.QualityTreatmentModels.Assessment;
import dev.mesbase.business.quality.domain.QualityTreatmentModels.Attribution;
import dev.mesbase.business.quality.domain.QualityTreatmentModels.State;
import dev.mesbase.common.core.page.PageData;
import dev.mesbase.core.error.ErrorCode;
import dev.mesbase.core.error.FoundationException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public final class ProductionDefectModels {
    private ProductionDefectModels() {
    }

    public record Facts(@NotNull Long executionId, @NotNull Long affectedNodeId,
                        @NotNull @DecimalMin(value = "0", inclusive = false) @Digits(integer = 14, fraction = 6) BigDecimal defectQty,
                        @NotBlank String wasteCategory, @NotBlank @Size(max = 500) String defectReason,
                        Long responsibleUserId,
                        @Size(max = 500) String responsibilityRemark,
                        @NotNull @PastOrPresent LocalDateTime occurredAt) {
    }

    public record Command(@NotBlank @Size(max = 64) String requestKey, Integer expectedRevision,
                          @Size(max = 500) String revisionReason, @Valid Facts facts, Assessment assessment) {
        public Command(String requestKey, Integer expectedRevision, String revisionReason, Facts facts) {
            this(requestKey, expectedRevision, revisionReason, facts, null);
        }
    }

    public record Names(Map<Long, String> nodes, Map<Long, String> users) {
    }

    public record ListContext(String declarationId, String workorderCode,
                              String productCode, String productName, String workorderStatus,
                              String executionStatus, String source, String batchNo, String specification) {
    }

    /**
     * Current workflow balance shared by the list and detail projections.
     */
    public record Processing(State state, BigDecimal pendingDispositionQty, BigDecimal returnedQty,
                             BigDecimal transferredQty) {
        public static Processing unresolved() {
            return new Processing(State.UNRESOLVED, null, null, null);
        }

        /**
         * Query-only projection: an unavailable balance does not prevent reading the record.
         */
        public static Processing read(String recordAction, Attribution attribution, BigDecimal declared,
                                      BigDecimal returned, BigDecimal transferred, long invalidActions) {
            if (invalidActions > 0 || attribution == null || declared == null || returned == null || transferred == null)
                return unresolved();
            try {
                return of(recordAction, attribution, declared, returned, transferred);
            } catch (FoundationException invalidBalance) {
                return unresolved();
            }
        }

        public static Processing of(String recordAction, Attribution attribution, BigDecimal declared,
                                    BigDecimal returned, BigDecimal transferred) {
            var pending = declared.subtract(returned).subtract(transferred);
            if (attribution == null || DefectRecordAction.from(recordAction) == null || declared.signum() <= 0 || returned.signum() < 0 || transferred.signum() < 0 || pending.signum() < 0
                    || attribution != Attribution.INDEPENDENT && (returned.signum() > 0 || transferred.signum() > 0))
                throw new FoundationException(ErrorCode.QUALITY_TREATMENT_CONFLICT);
            State state;
            if (DefectRecordAction.VOID.getValue().equals(recordAction)) state = State.VOIDED;
            else state = switch (attribution) {
                case UNVERIFIED -> State.UNVERIFIED;
                case OVERLAP -> State.OVERLAP;
                case INDEPENDENT -> {
                    if (pending.signum() > 0) yield State.PENDING;
                    if (returned.signum() > 0 && transferred.signum() > 0) yield State.DISPOSED;
                    yield returned.signum() > 0 ? State.RETURNED : State.TRANSFERRED;
                }
            };
            return new Processing(state, pending, returned, transferred);
        }
    }

    public record Page(List<MesProductionDefectEntity> items, long total, int offset, int limit, Names names,
                       Map<String, Processing> treatments,
                       Map<String, ListContext> contexts) implements PageData<MesProductionDefectEntity> {
    }

    public record NodeTotal(Long affectedNodeId, BigDecimal effectiveDefectQty, BigDecimal returnedQty,
                            BigDecimal unverifiedQty, BigDecimal directDefectQty) {
        public NodeTotal(Long affectedNodeId, BigDecimal effectiveDefectQty) {
            this(affectedNodeId, effectiveDefectQty, BigDecimal.ZERO, BigDecimal.ZERO, effectiveDefectQty);
        }
    }

    public record Node(String id, String name, int sequenceNo) {
    }

    public record Warehouse(String id, String code, String name) {
    }
}
