// MES-CODEGEN: scaffold; review required. RuoYi template pipeline adapted under MIT.
package dev.mesbase.modules.pro.domain;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;

/**
 * 生产工单表写入参数，字段来自已审查的表结构。
 */
public record ProWorkorderUpdateCommand(
        @NotBlank @Size(max = 64) String workorderCode,
        @NotBlank @Size(max = 255) String workorderName,
        @NotBlank @Size(max = 64) String orderSource,
        @Size(max = 64) String sourceCode,
        @NotNull Long productId,
        @NotBlank @Size(max = 64) String productCode,
        @NotBlank @Size(max = 255) String productName,
        @Size(max = 255) String productSpc,
        @NotBlank @Size(max = 64) String unitOfMeasure,
        @NotNull Double quantity,
        Double quantityProduced,
        Double quantityChanged,
        Double quantityScheduled,
        Long clientId,
        @Size(max = 64) String clientCode,
        @Size(max = 255) String clientName,
        @Size(max = 64) String batchCode,
        @NotNull LocalDateTime requestDate,
        @NotNull Long parentId,
        @NotBlank @Size(max = 500) String ancestors,
        LocalDateTime finishDate,
        @Size(max = 64) String status,
        @Size(max = 500) String remark,
        @Size(max = 64) String attr1,
        @Size(max = 255) String attr2,
        Integer attr3,
        Integer attr4,
        LocalDateTime transactionDate,
        @NotBlank @Size(max = 39) String moId
) {
}

