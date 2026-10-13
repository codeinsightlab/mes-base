package dev.mesbase.business.production.domain;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RoutingNodeCommand(
        @NotNull @Pattern(regexp = "[A-Za-z0-9_-]{1,64}") String nodeCode,
        @NotNull Long operationId,
        @NotNull Boolean isKey,
        @Size(max = 1000) String description,
        @NotNull @Min(0) Integer displayOrder
) {
}
