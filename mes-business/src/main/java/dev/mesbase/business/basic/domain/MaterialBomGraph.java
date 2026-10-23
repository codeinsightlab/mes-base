package dev.mesbase.business.basic.domain;

import java.math.BigDecimal;
import java.util.List;

public record MaterialBomGraph(Long bomId, BigDecimal outputQty, List<Node> items, List<Total> totals) {
    public record Node(String path, Long materialId, String code, String name, BigDecimal quantity, String unitName,
                       Long childBomId, List<Node> children) {
    }

    public record Total(Long materialId, String code, String name, BigDecimal quantity, String unitName) {
    }
}
