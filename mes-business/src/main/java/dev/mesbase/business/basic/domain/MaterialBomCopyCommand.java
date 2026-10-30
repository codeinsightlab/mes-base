package dev.mesbase.business.basic.domain;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record MaterialBomCopyCommand(@NotBlank @Size(max = 32) String version, @NotNull Long revision) {
}
