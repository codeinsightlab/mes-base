package dev.mesbase.system.dto;
@com.fasterxml.jackson.annotation.JsonIgnoreProperties({"createBy","createTime","updateBy","updateTime","params","seriaNowNo"})
public record AutoCodePartForm(Long partId, Long ruleId, Integer partIndex, String partType, String partCode, String partName, Integer partLength, String dateFormat, String inputCharacter, String fixCharacter, Integer seriaStartNo, Integer seriaStep, String cycleFlag, String cycleMethod, String remark) {
 public dev.mesbase.common.core.domain.entity.SysAutoCodePart toEntity(){var row=new dev.mesbase.common.core.domain.entity.SysAutoCodePart();
  row.setPartId(partId);
  row.setRuleId(ruleId);
  row.setPartIndex(partIndex);
  row.setPartType(partType);
  row.setPartCode(partCode);
  row.setPartName(partName);
  row.setPartLength(partLength);
  row.setDateFormat(dateFormat);
  row.setInputCharacter(inputCharacter);
  row.setFixCharacter(fixCharacter);
  row.setSeriaStartNo(seriaStartNo);
  row.setSeriaStep(seriaStep);
  row.setCycleFlag(cycleFlag);
  row.setCycleMethod(cycleMethod);
  row.setRemark(remark);
  return row;
 }
}
