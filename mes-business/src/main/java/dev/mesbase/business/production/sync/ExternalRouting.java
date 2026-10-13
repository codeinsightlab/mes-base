// MES-CODEGEN: reviewed lifecycle extension of the generated scaffold.
package dev.mesbase.business.production.sync;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Min;
import jakarta.validation.Valid;
import java.util.List;

public record ExternalRouting(
        @Size(max = 128) String externalId,
        @NotBlank @Size(max = 64) String code,
        @NotBlank @Size(max = 255) String name,
        @Size(max = 1000) String description,
        @Size(max = 128) String sourceVersion,
        @NotNull @Size(min = 1, max = 100) List<@NotBlank @Size(max = 64) String> productCodes,
        @NotNull @Size(min = 1, max = 100) List<@NotNull @Valid ExternalRoutingStep> steps
) {
}
