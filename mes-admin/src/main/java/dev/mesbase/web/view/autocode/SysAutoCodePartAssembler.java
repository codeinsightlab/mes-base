package dev.mesbase.web.view.autocode;

import dev.mesbase.common.core.domain.entity.SysAutoCodePart;
import java.util.List;

/** Explicit conversion at the Controller HTTP boundary. */
public final class SysAutoCodePartAssembler {
    private SysAutoCodePartAssembler() {
    }

    public static SysAutoCodePartResult toResult(SysAutoCodePart row) {
        if (row == null) return null;
        return new SysAutoCodePartResult(row.getPartId() == null ? null : row.getPartId().toString(),
                row.getRuleId() == null ? null : row.getRuleId().toString(),
                row.getPartIndex(),
                row.getPartType(),
                row.getPartCode(),
                row.getPartName(),
                row.getPartLength(),
                row.getDateFormat(),
                row.getInputCharacter(),
                row.getFixCharacter(),
                row.getSeriaStartNo(),
                row.getSeriaStep(),
                row.getCycleFlag(),
                row.getCycleMethod(),
                row.getRemark(),
                row.getCreateBy(),
                row.getCreateTime(),
                row.getUpdateBy(),
                row.getUpdateTime());
    }

    public static List<SysAutoCodePartResult> toResults(List<SysAutoCodePart> rows) {
        return rows == null ? null : rows.stream().map(SysAutoCodePartAssembler::toResult).toList();
    }
}
