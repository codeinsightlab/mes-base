package dev.mesbase.common.core.domain.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** SOURCE_PRODUCT_PORT: Source RegisterBody fields; no caller-selected roles or Factory. */
public record RegisterBody(
        @NotBlank @Size(min = 2, max = 20) String username,
        @NotBlank @Size(max = 128) String password,
        @Size(max = 32) String code,
        @Size(max = 36) String uuid) {}
