package dev.mesbase.web.view.autocode;

import java.util.Objects;

import dev.mesbase.common.core.domain.entity.SysAutoCodeRule;
import java.util.List;

/** Explicit conversion at the Controller HTTP boundary. */
public final class SysAutoCodeRuleAssembler {
    private SysAutoCodeRuleAssembler() {
    }

    public static SysAutoCodeRuleResult toResult(SysAutoCodeRule row) {
        if (row == null) return null;
        return new SysAutoCodeRuleResult(Objects.toString(row.getRuleId(), null),
                row.getRuleCode(),
                row.getRuleName(),
                row.getRuleDesc(),
                row.getMaxLength(),
                row.getIsPadded(),
                row.getPaddedChar(),
                row.getPaddedMethod(),
                row.getEnableFlag(),
                row.getRemark(),
                row.getCreateBy(),
                row.getCreateTime(),
                row.getUpdateBy(),
                row.getUpdateTime());
    }

    public static List<SysAutoCodeRuleResult> toResults(List<SysAutoCodeRule> rows) {
        if (rows == null) return null;
        return rows.stream().map(SysAutoCodeRuleAssembler::toResult).toList();
    }
}
