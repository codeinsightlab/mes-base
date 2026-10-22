package dev.mesbase.web.view.tree;

import java.util.Objects;

import dev.mesbase.common.core.domain.TreeSelect;
import java.util.List;

/** Explicit conversion at the Controller HTTP boundary. */
public final class TreeAssembler {
    private TreeAssembler() {
    }

    public static TreeResult toResult(TreeSelect tree) {
        if (tree == null) return null;
        return new TreeResult(Objects.toString(tree.getId(), null),
                tree.getLabel(),
                TreeAssembler.toResults(tree.getChildren()));
    }

    public static List<TreeResult> toResults(List<TreeSelect> rows) {
        if (rows == null) return null;
        return rows.stream().map(TreeAssembler::toResult).toList();
    }
}
