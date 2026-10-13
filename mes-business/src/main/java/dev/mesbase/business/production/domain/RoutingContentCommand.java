// MES-CODEGEN: reviewed lifecycle extension of the generated scaffold.
package dev.mesbase.business.production.domain;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Min;
import jakarta.validation.Valid;
import java.util.List;

public record RoutingContentCommand(
        @NotNull @Min(0) Integer revision,
        @NotNull @Size(max = 100) List<@NotNull Long> materialIds,
        @NotNull @Size(max = 100) List<@NotNull @Valid RoutingStepCommand> steps
) {
}
