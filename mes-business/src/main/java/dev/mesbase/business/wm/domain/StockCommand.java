package dev.mesbase.business.wm.domain;

import java.math.BigDecimal;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Digits;

/**
 * 客户端只表达意图；身份、单位、操作者、前后余额均由服务端确定。
 */
public record StockCommand(@NotBlank String requestId, @NotNull Long stockId,
                           @NotNull @Digits(integer = 14, fraction = 6) BigDecimal quantity,
                           @NotNull @Digits(integer = 14, fraction = 6) BigDecimal expectedQuantity,
                           @NotNull @Digits(integer = 14, fraction = 6) BigDecimal expectedAvailable,
                           @NotBlank @Size(max = 500) String reason, Long issueId, Long documentId) {
    public StockCommand(String requestId, Long stockId, BigDecimal quantity, BigDecimal expectedQuantity,
                        BigDecimal expectedAvailable, String reason, Long issueId) {
        this(requestId, stockId, quantity, expectedQuantity, expectedAvailable, reason, issueId, null);
    }
}
