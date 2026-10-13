package dev.mesbase.business.production.domain;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record RoutingEdgeCommand(
        @NotNull @Pattern(regexp = "[A-Za-z0-9_-]{1,64}") String fromNodeCode,
        @NotNull @Pattern(regexp = "[A-Za-z0-9_-]{1,64}") String toNodeCode
) {
}
