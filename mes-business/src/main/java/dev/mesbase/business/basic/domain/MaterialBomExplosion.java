package dev.mesbase.business.basic.domain;

import java.math.BigDecimal;
import java.util.List;

/**
 * Read-only structure and known leaf demand; never fulfillment or inventory evidence.
 */
public record MaterialBomExplosion(MaterialEntity material, BigDecimal outputQty, MaterialBomEntity selectedVersion,
                                   Versions versions, boolean selectionRequired, boolean complete,
                                   List<Node> items, List<Total> totals, List<Problem> problems) {
    public record Versions(List<MaterialBomEntity> items, long total, int offset, int limit) {
    }

    public record Node(String path, int level, Long lineId, Long parentBomId, String parentBomVersion,
                       Long materialId, String code, String name, Long unitId, String unitName,
                       BigDecimal componentQty, BigDecimal baseQty, BigDecimal quantity, Long childBomId,
                       boolean leaf, List<Node> children) {
    }

    public record Total(Long materialId, String code, String name, Long unitId, String unitName,
                        BigDecimal quantity, List<String> paths) {
    }

    public record Problem(String path, Long bomId, Long lineId, String errorCode, String message) {
    }
}
