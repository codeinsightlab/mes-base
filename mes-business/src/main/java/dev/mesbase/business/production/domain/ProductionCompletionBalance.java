package dev.mesbase.business.production.domain;

import dev.mesbase.business.production.domain.ProductionReportModels.ExecutionQuantity;
import java.math.BigDecimal;

/**
 * Exact conservation in output units. Never round historical facts or sum reports across operations.
 */
public final class ProductionCompletionBalance {
    private ProductionCompletionBalance() {
    }

    public static String blockedReason(ExecutionQuantity quantities) {
        if (quantities.confirmedInputQty().signum() <= 0 || quantities.reservedInputQty().signum() != 0)
            return "缺少正式投入或仍有未决投产意图";
        if (quantities.routingId() == null) {
            if (!quantities.nodes().isEmpty()) return "无工艺执行存在不一致的工序数量";
            return null;
        }
        if (quantities.nodes().isEmpty()) return "缺少可核实的正式生产路径";
        if (quantities.nodes().stream().anyMatch(n -> n.rawEffectiveOutputQty().signum() < 0
                || n.unverifiedDefectQty().signum() != 0 || n.netDefectQty().signum() < 0))
            return "存在数量冲突或待核实不良，不能完工";
        BigDecimal loss = quantities.nodes().stream().map(n -> n.netDefectQty())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal output = quantities.nodes().getLast().effectiveOutputQty();
        return quantities.confirmedInputQty().compareTo(output.add(loss)) == 0 ? null
                : "投入数量不等于末道有效报工与最终不良损失之和，不能完工";
    }
}
