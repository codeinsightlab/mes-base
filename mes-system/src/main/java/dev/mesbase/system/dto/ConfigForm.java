package dev.mesbase.system.dto;
@com.fasterxml.jackson.annotation.JsonIgnoreProperties({"createTime","updateTime","createBy","updateBy","params","dept","roles","children","ancestors","delFlag","admin","flag","avatar","loginDate","loginIp","minioUrl","wxOpenId"})
public record ConfigForm(Long configId, String configName, String configKey, String configValue, String configType, String remark) {
 public dev.mesbase.system.domain.SysConfig toEntity(){var result=new dev.mesbase.system.domain.SysConfig();
result.setConfigId(configId);
result.setConfigName(configName);
result.setConfigKey(configKey);
result.setConfigValue(configValue);
result.setConfigType(configType);
result.setRemark(remark);
return result;}
}
