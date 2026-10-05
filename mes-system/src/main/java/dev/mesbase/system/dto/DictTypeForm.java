package dev.mesbase.system.dto;
@com.fasterxml.jackson.annotation.JsonIgnoreProperties({"createTime","updateTime","createBy","updateBy","params","dept","roles","children","ancestors","delFlag","admin","flag","avatar","loginDate","loginIp","minioUrl","wxOpenId"})
public record DictTypeForm(Long dictId, String dictName, String dictType, String status, String remark) {
 public dev.mesbase.common.core.domain.entity.SysDictType toEntity(){var result=new dev.mesbase.common.core.domain.entity.SysDictType();
result.setDictId(dictId);
result.setDictName(dictName);
result.setDictType(dictType);
result.setStatus(status);
result.setRemark(remark);
return result;}
}
