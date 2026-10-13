package dev.mesbase.business.issue.domain;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/**
 * 领料出库明细写入参数，字段来自已审查的表结构。
 */
public record MesMaterialIssueItemUpdateCommand(
        @NotNull Long issueId,
        @NotNull Long materialId,
        @Size(max = 128) String batchNo,
        @NotNull @Digits(integer = 14, fraction = 6) BigDecimal quantity,
        Long unitId,
        Long warehouseId,
        @Size(max = 500) String remark
) {
}

