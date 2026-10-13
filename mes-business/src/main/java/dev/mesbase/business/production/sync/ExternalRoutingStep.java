package dev.mesbase.business.production.sync;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Min;

public record ExternalRoutingStep(
        @NotNull @Min(1) Integer sequence,
        @NotBlank @Size(max = 64) String operationCode,
        @NotNull Boolean isKey,
        @Size(max = 1000) String description
) {
}
