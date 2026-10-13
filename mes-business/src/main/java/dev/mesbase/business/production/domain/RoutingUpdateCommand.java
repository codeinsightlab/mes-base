// MES-CODEGEN: reviewed lifecycle extension of the generated scaffold.
package dev.mesbase.business.production.domain;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Min;

public record RoutingUpdateCommand(
        @NotBlank @Size(max = 255) String name,
        @Size(max = 1000) String description,
        @NotNull @Min(0) Integer revision
) {
}
