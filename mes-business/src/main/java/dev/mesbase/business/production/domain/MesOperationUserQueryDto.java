package dev.mesbase.business.production.domain;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/**
 * 工序能力人员分页查询条件。
 */
public record MesOperationUserQueryDto(
        @Min(0) Integer offset,
        @Min(1) @Max(100) Integer limit,
        Long operationId,
        Long userId,
        @Size(max = 500) String remark
) {
}
