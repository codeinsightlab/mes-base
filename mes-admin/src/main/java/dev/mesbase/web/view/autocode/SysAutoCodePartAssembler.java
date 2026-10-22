package dev.mesbase.web.view.autocode;

import dev.mesbase.common.core.domain.entity.SysAutoCodePart;
import dev.mesbase.common.utils.bean.BeanConversions;
import java.util.List;

/**
 * Explicit conversion at the Controller HTTP boundary.
 */
public final class SysAutoCodePartAssembler {
    private SysAutoCodePartAssembler() {
    }

    public static SysAutoCodePartResult toResult(SysAutoCodePart row) {
        return BeanConversions.convert(row, SysAutoCodePartResult.class);
    }

    public static List<SysAutoCodePartResult> toResults(List<SysAutoCodePart> rows) {
        if (rows == null) return null;
        return rows.stream().map(SysAutoCodePartAssembler::toResult).toList();
    }
}
