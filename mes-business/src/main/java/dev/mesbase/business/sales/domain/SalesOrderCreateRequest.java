package dev.mesbase.business.sales.domain;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;

public record SalesOrderCreateRequest(
        @NotNull Long customerId,
        @NotNull LocalDate orderDate,
        @Size(max = 500) String remark,
        @NotEmpty @Size(max = 200) List<@NotNull @Valid SalesOrderLineRequest> items) {
}
