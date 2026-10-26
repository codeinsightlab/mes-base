package dev.mesbase.business.production.domain;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;

public record ProductionPlanCreateRequest(
        @NotNull LocalDate planDate,
        @Size(max = 500) String remark,
        @NotEmpty @Size(max = 200) List<@NotNull @Valid ProductionPlanLineRequest> items) {
}
