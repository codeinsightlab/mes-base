package dev.mesbase.web.view.tree;

import java.util.List;

/** HTTP response whitelist; persistence and credential fields are intentionally absent. */
public record TreeResult(String id, String label, List<TreeResult> children) {
}
