package dev.mesbase.business.production.sync;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Min;

public record ExternalOperation(
        @Size(max = 128) String externalId,
        @NotBlank @Size(max = 64) String code,
        @NotBlank @Size(max = 255) String name,
        @Size(max = 1000) String description,
        @NotBlank String status
) {
}
