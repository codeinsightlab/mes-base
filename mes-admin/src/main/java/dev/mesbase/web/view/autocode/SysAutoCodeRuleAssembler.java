package dev.mesbase.web.view.autocode;

import dev.mesbase.common.utils.bean.BeanConversions;
import java.util.Objects;

import dev.mesbase.common.core.domain.entity.SysAutoCodeRule;
import java.util.List;

/** Explicit conversion at the Controller HTTP boundary. */
public final class SysAutoCodeRuleAssembler {
    private SysAutoCodeRuleAssembler() {
    }

    public static SysAutoCodeRuleResult toResult(SysAutoCodeRule row) {
        return BeanConversions.convert(row, SysAutoCodeRuleResult.class);
    }

    public static List<SysAutoCodeRuleResult> toResults(List<SysAutoCodeRule> rows) {
        if (rows == null) return null;
        return rows.stream().map(SysAutoCodeRuleAssembler::toResult).toList();
    }
}
