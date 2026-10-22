package dev.mesbase.web.view.post;

import java.util.Optional;

import java.util.Objects;

import dev.mesbase.system.domain.SysPost;
import java.util.List;

/** Explicit conversion at the Controller HTTP boundary. */
public final class SysPostAssembler {
    private SysPostAssembler() {
    }

    public static SysPostResult toResult(SysPost row) {
        if (row == null) return null;
        return new SysPostResult(Objects.toString(row.getPostId(), null),
                row.getPostCode(),
                row.getPostName(),
                Optional.ofNullable(row.getPostSort()).map(nullableValue -> Integer.valueOf(nullableValue)).orElse(null),
                row.getStatus(),
                row.isFlag(),
                row.getCreateBy(),
                row.getCreateTime(),
                row.getUpdateBy(),
                row.getUpdateTime(),
                row.getRemark());
    }

    public static List<SysPostResult> toResults(List<SysPost> rows) {
        if (rows == null) return null;
        return rows.stream().map(SysPostAssembler::toResult).toList();
    }
}
