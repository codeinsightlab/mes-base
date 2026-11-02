package dev.mesbase.business.production.domain;

import dev.mesbase.common.validation.ParameterChecks;
import dev.mesbase.core.error.ErrorCode;
import dev.mesbase.core.error.SafeBusinessMessage;
import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Cumulative material rounding is applied once to the output total, never independently per issue.
 */
public record ProductionInputQuota(BigDecimal outputQty, BigDecimal materialQty, long invalidCount) {
    public static BigDecimal materialFor(BigDecimal output, BigDecimal base, BigDecimal component) {
        if (output == null || base == null || component == null || output.signum() < 0
                || base.signum() <= 0 || component.signum() <= 0)
            ParameterChecks.reject(ErrorCode.BAD_REQUEST,
                    new SafeBusinessMessage("计划数量及BOM基数、组件用量必须为有效正数"));
        var result = output.multiply(component).divide(base, 6, RoundingMode.HALF_UP);
        if (result.precision() - result.scale() > 14)
            ParameterChecks.reject(ErrorCode.BAD_REQUEST, new SafeBusinessMessage("计算后的用料数量超出系统精度范围"));
        return result;
    }

    public BigDecimal next(BigDecimal plan, BigDecimal output, BigDecimal base, BigDecimal component) {
        if (invalidCount != 0 || outputQty == null || materialQty == null || plan == null
                || output == null || output.signum() <= 0 || outputQty.signum() < 0 || materialQty.signum() < 0)
            ParameterChecks.reject(ErrorCode.RESOURCE_CONFLICT,
                    new SafeBusinessMessage("当前生产投入记录与工单数量依据不一致，暂不能继续，请联系管理员核查"));
        if (outputQty.add(output).compareTo(plan) > 0)
            ParameterChecks.reject(ErrorCode.PRODUCTION_INPUT_QUOTA_EXCEEDED);
        var target = materialFor(outputQty.add(output), base, component);
        var amount = target.subtract(materialQty);
        if (amount.signum() <= 0 || target.compareTo(materialFor(plan, base, component)) > 0)
            ParameterChecks.reject(ErrorCode.RESOURCE_CONFLICT,
                    new SafeBusinessMessage("当前累计投入量与本次配套数量不一致，暂不能继续，请联系管理员核查"));
        return amount;
    }
}
