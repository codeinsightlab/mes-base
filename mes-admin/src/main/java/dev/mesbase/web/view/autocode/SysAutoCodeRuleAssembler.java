package dev.mesbase.web.view.autocode;

import dev.mesbase.common.core.domain.entity.SysAutoCodeRule;
import java.util.List;

/** Explicit conversion at the Controller HTTP boundary. */
public final class SysAutoCodeRuleAssembler {
    private SysAutoCodeRuleAssembler() {
    }

    public static SysAutoCodeRuleResult toResult(SysAutoCodeRule row) {
        if (row == null) return null;
        return new SysAutoCodeRuleResult(row.getRuleId() == null ? null : row.getRuleId().toString(),
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
        return rows == null ? null : rows.stream().map(SysAutoCodeRuleAssembler::toResult).toList();
    }
}
