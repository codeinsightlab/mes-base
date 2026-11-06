package dev.mesbase.business.purchase.service.impl;

import java.math.BigDecimal;
import java.math.RoundingMode;
import dev.mesbase.common.validation.ParameterChecks;
import dev.mesbase.core.error.ErrorCode;
import dev.mesbase.core.error.SafeBusinessMessage;

/**
 * Purchase quotes and transaction snapshots use material base units and CNY; never inventory valuation.
 */
public final class PurchasePriceRules {
    private PurchasePriceRules() {
    }

    public static void reject(String message) {
        ParameterChecks.reject(ErrorCode.BAD_REQUEST, new SafeBusinessMessage(message));
    }

    public static BigDecimal price(BigDecimal value, String remark) {
        if (value == null || value.signum() < 0 || value.scale() > 6 || value.precision() - value.scale() > 14)
            reject("采购单价须为非负数，最多14位整数、6位小数");
        if (value.signum() == 0 && (remark == null || remark.isBlank())) reject("零价格请在备注中说明原因");
        return value.setScale(6, RoundingMode.UNNECESSARY);
    }

    public static void currency(String currency) {
        if (!"CNY".equals(currency)) reject("当前采购价格仅支持人民币 CNY");
    }

    public static BigDecimal amount(BigDecimal quantity, BigDecimal price) {
        if (quantity == null || quantity.signum() <= 0) reject("采购数量必须大于零");
        return quantity.multiply(price).setScale(2, RoundingMode.HALF_UP);
    }
}
