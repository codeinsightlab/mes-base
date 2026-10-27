package dev.mesbase.business.production.controller;

import tools.jackson.databind.JsonNode;
import dev.mesbase.business.production.domain.WorkOrderModels;
import dev.mesbase.core.error.ErrorCode;
import dev.mesbase.core.error.FoundationException;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Legacy batches is parsed only to return a clear refusal; it never reaches domain persistence.
 */
public record WorkOrderSaveRequest(@Size(max = 64) String code, @NotNull Long productId,
                                   @NotNull BigDecimal plannedQty, @NotNull Long routingId,
                                   LocalDateTime plannedStartAt, LocalDateTime plannedEndAt,
                                   @Size(max = 128) String externalId, @Size(max = 128) String externalCode,
                                   @Size(max = 500) String remark, Integer revision, JsonNode batches) {
    public WorkOrderModels.Save toCommand() {
        if (batches != null) throw new FoundationException(ErrorCode.LEGACY_BATCH_READ_ONLY);
        return new WorkOrderModels.Save(code, productId, plannedQty, routingId, plannedStartAt, plannedEndAt,
                externalId, externalCode, remark, revision);
    }
}
