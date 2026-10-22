package dev.mesbase.web.view.dict;

import dev.mesbase.common.core.domain.entity.SysDictType;
import dev.mesbase.common.utils.bean.BeanConversions;
import java.util.List;

/**
 * Explicit conversion at the Controller HTTP boundary.
 */
public final class SysDictTypeAssembler {
    private SysDictTypeAssembler() {
    }

    public static SysDictTypeResult toResult(SysDictType row) {
        return BeanConversions.convert(row, SysDictTypeResult.class);
    }

    public static List<SysDictTypeResult> toResults(List<SysDictType> rows) {
        if (rows == null) return null;
        return rows.stream().map(SysDictTypeAssembler::toResult).toList();
    }
}
