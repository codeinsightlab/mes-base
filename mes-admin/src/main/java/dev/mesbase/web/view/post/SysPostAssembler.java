package dev.mesbase.web.view.post;

import dev.mesbase.system.domain.SysPost;
import java.util.List;

/** Explicit conversion at the Controller HTTP boundary. */
public final class SysPostAssembler {
    private SysPostAssembler() {
    }

    public static SysPostResult toResult(SysPost row) {
        if (row == null) return null;
        return new SysPostResult(row.getPostId() == null ? null : row.getPostId().toString(),
                row.getPostCode(),
                row.getPostName(),
                row.getPostSort() == null ? null : Integer.valueOf(row.getPostSort()),
                row.getStatus(),
                row.isFlag(),
                row.getCreateBy(),
                row.getCreateTime(),
                row.getUpdateBy(),
                row.getUpdateTime(),
                row.getRemark());
    }

    public static List<SysPostResult> toResults(List<SysPost> rows) {
        return rows == null ? null : rows.stream().map(SysPostAssembler::toResult).toList();
    }
}
