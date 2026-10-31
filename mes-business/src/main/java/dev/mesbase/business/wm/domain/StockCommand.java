package dev.mesbase.business.wm.domain;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/**
 * 客户端只表达意图；身份、单位、操作者、前后余额均由服务端确定。
 */
public record StockCommand(@NotBlank String requestId, @NotNull Long stockId,
                           @NotNull @Digits(integer = 14, fraction = 6) BigDecimal quantity,
                           @NotNull @Digits(integer = 14, fraction = 6) BigDecimal expectedQuantity,
                           @NotNull @Digits(integer = 14, fraction = 6) BigDecimal expectedAvailable,
                           @NotBlank @Size(max = 500) String reason, Long documentId, Long documentLineId) {
    public StockCommand(String requestId, Long stockId, BigDecimal quantity, BigDecimal expectedQuantity,
                        BigDecimal expectedAvailable, String reason, Long documentId) {
        this(requestId, stockId, quantity, expectedQuantity, expectedAvailable, reason, documentId, null);
    }

    public StockCommand(String requestId, Long stockId, BigDecimal quantity, BigDecimal expectedQuantity,
                        BigDecimal expectedAvailable, String reason) {
        this(requestId, stockId, quantity, expectedQuantity, expectedAvailable, reason, null, null);
    }
}
