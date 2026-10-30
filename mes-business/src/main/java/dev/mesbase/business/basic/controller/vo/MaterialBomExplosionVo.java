package dev.mesbase.business.basic.controller.vo;

import java.util.List;

public record MaterialBomExplosionVo(Material material, String outputQty, Version selectedVersion,
                                     Versions versions, boolean selectionRequired, boolean complete,
                                     List<Node> items, List<Total> totals, List<Problem> problems) {
    public record Material(String id, String code, String name, String unitId, String status) {
    }

    public record Version(String id, String code, String version, String status, String baseQty,
                          String unitId, Boolean defaultVersion) {
    }

    public record Versions(List<Version> items, long total, int offset, int limit) {
    }

    public record Node(String path, int level, String lineId, String parentBomId, String parentBomVersion,
                       String materialId, String code, String name, String unitId, String unitName,
                       String componentQty, String baseQty, String quantity, String childBomId,
                       boolean leaf, List<Node> children) {
    }

    public record Total(String materialId, String code, String name, String unitId, String unitName,
                        String quantity, List<String> paths) {
    }

    public record Problem(String path, String bomId, String lineId, String errorCode, String message) {
    }
}
