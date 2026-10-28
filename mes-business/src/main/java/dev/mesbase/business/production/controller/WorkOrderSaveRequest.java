package dev.mesbase.business.production.controller;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import dev.mesbase.business.production.domain.WorkOrderModels;
import dev.mesbase.common.validation.ParameterChecks;
import dev.mesbase.core.error.ErrorCode;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record WorkOrderSaveRequest(@Size(max = 64) String code, @NotNull Long productId,
                                   @NotNull BigDecimal plannedQty, @NotNull Long routingId,
                                   LocalDateTime plannedStartAt, LocalDateTime plannedEndAt,
                                   @Size(max = 128) String externalId, @Size(max = 128) String externalCode,
                                   @Size(max = 500) String remark, Integer revision, @Size(max = 64) String ruleCode) {
    @JsonAnySetter
    public void rejectUnexpected(String field, Object value) {
        ParameterChecks.reject(ErrorCode.BAD_REQUEST);
    }

    public WorkOrderModels.Save toCommand() {
        return new WorkOrderModels.Save(code, productId, plannedQty, routingId, plannedStartAt, plannedEndAt,
                externalId, externalCode, remark, revision, ruleCode);
    }
}
