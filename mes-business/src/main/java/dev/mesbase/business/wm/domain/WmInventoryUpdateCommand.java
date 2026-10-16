package dev.mesbase.business.wm.domain;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/**
 * 库存管理写入参数，字段来自已审查的表结构。
 */
public record WmInventoryUpdateCommand(
        @NotNull Long materialId,
        @NotNull Long warehouseId,
        @NotNull @Digits(integer = 14, fraction = 6) BigDecimal quantity,
        @Digits(integer = 14, fraction = 6) BigDecimal availableQuantity,
        @NotBlank @Size(max = 16) String status,
        @Size(max = 500) String remark,
        @Size(max = 128) String batchNo,
        @Size(max = 64) String binCode
) {
}

