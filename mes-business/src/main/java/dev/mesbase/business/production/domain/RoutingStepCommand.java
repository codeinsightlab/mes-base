// MES-CODEGEN: reviewed lifecycle extension of the generated scaffold.
package dev.mesbase.business.production.domain;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Min;

public record RoutingStepCommand(
        @NotNull @Min(1) Integer sequence,
        @NotNull Long operationId,
        @NotNull Boolean isKey,
        @Size(max = 1000) String description
) {
}
