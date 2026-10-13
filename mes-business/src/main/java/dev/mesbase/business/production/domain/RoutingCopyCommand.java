package dev.mesbase.business.production.domain;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Min;

public record RoutingCopyCommand(
        @NotNull @Min(0) Integer revision,
        @NotBlank @Size(max = 64) String code,
        @NotBlank @Size(max = 255) String name
) {
}
