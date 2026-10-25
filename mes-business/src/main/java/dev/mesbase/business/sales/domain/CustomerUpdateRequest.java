package dev.mesbase.business.sales.domain;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CustomerUpdateRequest(
        @NotNull Integer revision,
        @NotBlank @Size(max = 64) String code,
        @NotBlank @Size(max = 255) String name,
        @Size(max = 128) String shortName,
        @Size(max = 128) String contactPerson,
        @Size(max = 64) String contactPhone,
        @Size(max = 500) String address,
        @NotNull @Pattern(regexp = "ENABLED|DISABLED") String status,
        @Size(max = 500) String remark) {
}
