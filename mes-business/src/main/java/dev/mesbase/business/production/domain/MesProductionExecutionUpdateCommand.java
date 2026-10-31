package dev.mesbase.business.production.domain;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 生产执行单元写入参数，字段来自已审查的表结构。
 */
public record MesProductionExecutionUpdateCommand(
        @NotNull Long workorderId,
        Long routingId,
        @NotNull @Digits(integer = 14, fraction = 6) BigDecimal targetQty,
        @NotBlank @Size(max = 20) String status,
        LocalDateTime startTime,
        LocalDateTime endTime,
        @Size(max = 500) String remark
) {
}

