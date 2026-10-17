package dev.mesbase.business.quality.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public final class QualityTreatmentModels {
    private QualityTreatmentModels() {
    }

    public enum Action {ASSESS, ALLOCATE, START, RESULT}

    public enum State {UNVERIFIED, OVERLAP, PENDING, IN_PROGRESS, WAITING_REWORK, FAILED_SCRAPPED, RECOVERED, SCRAPPED, VOIDED}

    public enum Attribution {INDEPENDENT, OVERLAP, UNVERIFIED}

    public record Assessment(Attribution type, String relatedDeclarationId, String basis) {
    }

    public record Command(String requestId, int expectedRevision, Action action, String parentId,
                          BigDecimal scrapQty, BigDecimal reworkQty, BigDecimal processedQty,
                          BigDecimal recoveredQty, BigDecimal failedQty, LocalDateTime occurredAt,
                          String reason, Assessment assessment) {
    }

    public record Summary(String declarationId, int revision, Long executionId, Long routingNodeId,
                          BigDecimal defectQty, Attribution attribution, String relatedDeclarationId, String basis,
                          BigDecimal allocatedScrapQty, BigDecimal allocatedReworkQty, BigDecimal startedQty,
                          BigDecimal processedQty, BigDecimal recoveredQty, BigDecimal failedQty,
                          BigDecimal pendingDispositionQty, BigDecimal pendingStartQty, BigDecimal inProgressQty,
                          BigDecimal netDefectQty, String state, String recordAction, String destinationType,
                          List<MesQualityTreatmentEntity> events) {
    }
}
