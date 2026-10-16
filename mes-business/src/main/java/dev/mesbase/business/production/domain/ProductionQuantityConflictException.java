package dev.mesbase.business.production.domain;

import dev.mesbase.core.error.ErrorCode;
import dev.mesbase.core.error.FoundationException;
import java.math.BigDecimal;

/**
 * Safe quantity facts only; never contains database errors or caller-supplied descriptions.
 */
public final class ProductionQuantityConflictException extends FoundationException {
    private final String explanation;

    public ProductionQuantityConflictException(Long node, Long upstream, BigDecimal input, BigDecimal reported,
                                               BigDecimal defect) {
        super(ErrorCode.REPORT_QUANTITY_CONFLICT);
        explanation = "节点 " + node + " 数量冲突：已有累计报工 " + reported.toPlainString()
                + "，有效不良 " + defect.toPlainString() + "，最大累计输入 " + input.toPlainString()
                + (upstream == null ? "（执行目标数量）" : "；上游影响节点 " + upstream + " 调整后有效产出 " + input.toPlainString())
                + "。须满足 0≤不良≤报工≤输入，请先核对历史事实。";
    }

    public String safeExplanation() {
        return explanation;
    }
}
