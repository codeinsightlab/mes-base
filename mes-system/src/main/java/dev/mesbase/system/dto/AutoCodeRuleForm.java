package dev.mesbase.system.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import dev.mesbase.common.core.domain.entity.SysAutoCodeRule;

@JsonIgnoreProperties({"createBy", "createTime", "updateBy", "updateTime", "params", "seriaNowNo"})
public record AutoCodeRuleForm(Long ruleId, String ruleCode, String ruleType, String ruleName, String ruleDesc,
                               Integer maxLength, String isPadded, String paddedChar, String paddedMethod,
                               String enableFlag, String remark) {
    public SysAutoCodeRule toEntity() {
        var row = new SysAutoCodeRule();
        row.setRuleId(ruleId);
        row.setRuleCode(ruleCode);
        row.setRuleType(ruleType);
        row.setRuleName(ruleName);
        row.setRuleDesc(ruleDesc);
        row.setMaxLength(maxLength);
        row.setIsPadded(isPadded);
        row.setPaddedChar(paddedChar);
        row.setPaddedMethod(paddedMethod);
        row.setEnableFlag(enableFlag);
        row.setRemark(remark);
        return row;
    }
}
