package dev.mesbase.business.basic.controller.vo;

import java.util.List;

public record MaterialBomGraphVo(String bomId, String outputQty, List<Node> items, List<Total> totals) {
    public record Node(String path, String materialId, String code, String name, String quantity, String unitName,
                       String childBomId, String childBomCode, String childBomVersion, List<Node> children) {
    }

    public record Total(String materialId, String code, String name, String quantity, String unitName) {
    }
}
