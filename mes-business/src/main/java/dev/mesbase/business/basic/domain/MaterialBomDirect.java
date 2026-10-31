package dev.mesbase.business.basic.domain;

import java.math.BigDecimal;
import java.util.List;

/**
 * The selected root's direct components, independently of lower-level manufacturing.
 */
public record MaterialBomDirect(MaterialBomEntity version, List<Component> items) {
    public record Component(Long lineId, Long materialId, Long unitId, BigDecimal componentQty,
                            BigDecimal baseQty, BigDecimal quantity) {
    }
}
