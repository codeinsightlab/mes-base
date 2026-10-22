package dev.mesbase.web.view.dict;

import dev.mesbase.common.core.domain.entity.SysDictData;
import dev.mesbase.common.utils.bean.BeanConversions;
import java.util.List;

/**
 * Explicit conversion at the Controller HTTP boundary.
 */
public final class SysDictDataAssembler {
    private SysDictDataAssembler() {
    }

    public static SysDictDataResult toResult(SysDictData row) {
        return BeanConversions.convert(row, SysDictDataResult.class);
    }

    public static List<SysDictDataResult> toResults(List<SysDictData> rows) {
        if (rows == null) return null;
        return rows.stream().map(SysDictDataAssembler::toResult).toList();
    }
}
