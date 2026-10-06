package dev.mesbase.web.view.dict;

import dev.mesbase.common.core.domain.entity.SysDictData;
import java.util.List;

/** Explicit conversion at the Controller HTTP boundary. */
public final class SysDictDataAssembler {
    private SysDictDataAssembler() {
    }

    public static SysDictDataResult toResult(SysDictData row) {
        if (row == null) return null;
        return new SysDictDataResult(row.getDictCode() == null ? null : row.getDictCode().toString(),
                row.getDictSort(),
                row.getDictLabel(),
                row.getDictValue(),
                row.getDictType(),
                row.getCssClass(),
                row.getListClass(),
                row.getIsDefault(),
                row.getStatus(),
                row.getCreateBy(),
                row.getCreateTime(),
                row.getUpdateBy(),
                row.getUpdateTime(),
                row.getRemark());
    }

    public static List<SysDictDataResult> toResults(List<SysDictData> rows) {
        return rows == null ? null : rows.stream().map(SysDictDataAssembler::toResult).toList();
    }
}
