package dev.mesbase.business.production.sync;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record ExternalRoutingEdge(
        @NotNull @Pattern(regexp = "[A-Za-z0-9_-]{1,64}") String fromNodeCode,
        @NotNull @Pattern(regexp = "[A-Za-z0-9_-]{1,64}") String toNodeCode
) {
}
