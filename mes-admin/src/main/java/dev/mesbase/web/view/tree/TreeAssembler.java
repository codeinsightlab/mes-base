package dev.mesbase.web.view.tree;

import dev.mesbase.common.core.domain.TreeSelect;
import java.util.List;

/** Explicit conversion at the Controller HTTP boundary. */
public final class TreeAssembler {
    private TreeAssembler() {
    }

    public static TreeResult toResult(TreeSelect tree) {
        if (tree == null) return null;
        return new TreeResult(tree.getId() == null ? null : tree.getId().toString(),
                tree.getLabel(),
                TreeAssembler.toResults(tree.getChildren()));
    }

    public static List<TreeResult> toResults(List<TreeSelect> rows) {
        return rows == null ? null : rows.stream().map(TreeAssembler::toResult).toList();
    }
}
