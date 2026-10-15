package dev.mesbase.business.production.domain;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import org.springframework.format.annotation.DateTimeFormat;

/**
 * 生产节点派工分页查询条件。
 */
public record MesExecutionAssignmentQueryDto(
        @Min(0) Integer offset,
        @Min(1) @Max(100) Integer limit,
        Long executionId,
        Long routingNodeId,
        Long userId,
        Long assignedBy,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime assignedAt,
        @Size(max = 500) String remark
) {
}
