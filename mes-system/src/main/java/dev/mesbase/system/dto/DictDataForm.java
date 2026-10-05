package dev.mesbase.system.dto;
@com.fasterxml.jackson.annotation.JsonIgnoreProperties({"createTime","updateTime","createBy","updateBy","params","dept","roles","children","ancestors","delFlag","admin","flag","avatar","loginDate","loginIp","minioUrl","wxOpenId"})
public record DictDataForm(Long dictCode, Long dictSort, String dictLabel, String dictValue, String dictType, String cssClass, String listClass, String isDefault, String status, String remark) {
 public dev.mesbase.common.core.domain.entity.SysDictData toEntity(){var result=new dev.mesbase.common.core.domain.entity.SysDictData();
result.setDictCode(dictCode);
result.setDictSort(dictSort);
result.setDictLabel(dictLabel);
result.setDictValue(dictValue);
result.setDictType(dictType);
result.setCssClass(cssClass);
result.setListClass(listClass);
result.setIsDefault(isDefault);
result.setStatus(status);
result.setRemark(remark);
return result;}
}
