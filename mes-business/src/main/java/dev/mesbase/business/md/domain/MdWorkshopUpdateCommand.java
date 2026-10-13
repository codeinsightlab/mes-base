package dev.mesbase.business.md.domain;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/**
 * 车间写入参数，字段来自已审查的表结构。
 */
public record MdWorkshopUpdateCommand(

        @NotBlank @Size(max=64) String workshopCode,
        @NotBlank @Size(max=255) String workshopName,
        @Digits(integer=16,fraction=2) BigDecimal area,
        @Size(max=64) String charge,
        Long deptId,
        @NotBlank @Size(max=1) String enableFlag,
        @Size(max=500) String remark
) {
}

