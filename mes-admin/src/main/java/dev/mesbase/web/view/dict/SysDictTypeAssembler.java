package dev.mesbase.web.view.dict;

import java.util.Objects;

import dev.mesbase.common.core.domain.entity.SysDictType;
import java.util.List;

/** Explicit conversion at the Controller HTTP boundary. */
public final class SysDictTypeAssembler {
    private SysDictTypeAssembler() {
    }

    public static SysDictTypeResult toResult(SysDictType row) {
        if (row == null) return null;
        return new SysDictTypeResult(Objects.toString(row.getDictId(), null),
                row.getDictName(),
                row.getDictType(),
                row.getStatus(),
                row.getCreateBy(),
                row.getCreateTime(),
                row.getUpdateBy(),
                row.getUpdateTime(),
                row.getRemark());
    }

    public static List<SysDictTypeResult> toResults(List<SysDictType> rows) {
        if (rows == null) return null;
        return rows.stream().map(SysDictTypeAssembler::toResult).toList();
    }
}
