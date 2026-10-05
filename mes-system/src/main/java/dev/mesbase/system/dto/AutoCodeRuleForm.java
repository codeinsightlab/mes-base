package dev.mesbase.system.dto;
@com.fasterxml.jackson.annotation.JsonIgnoreProperties({"createBy","createTime","updateBy","updateTime","params","seriaNowNo"})
public record AutoCodeRuleForm(Long ruleId, String ruleCode, String ruleName, String ruleDesc, Integer maxLength, String isPadded, String paddedChar, String paddedMethod, String enableFlag, String remark) {
 public dev.mesbase.common.core.domain.entity.SysAutoCodeRule toEntity(){var row=new dev.mesbase.common.core.domain.entity.SysAutoCodeRule();
  row.setRuleId(ruleId);
  row.setRuleCode(ruleCode);
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
