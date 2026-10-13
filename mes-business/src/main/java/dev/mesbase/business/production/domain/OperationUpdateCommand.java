package dev.mesbase.business.production.domain;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Min;

public record OperationUpdateCommand(
        @NotBlank @Size(max = 64) String code,
        @NotBlank @Size(max = 255) String name,
        @Size(max = 1000) String description,
        @NotBlank String status,
        @NotNull @Min(0) Integer sort,
        @Size(max = 128) String externalId,
        @NotNull @Min(0) Integer revision
) {
}
