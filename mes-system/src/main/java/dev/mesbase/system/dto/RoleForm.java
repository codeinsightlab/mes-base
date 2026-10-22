package dev.mesbase.system.dto;

import java.util.Objects;
@com.fasterxml.jackson.annotation.JsonIgnoreProperties({"createTime","updateTime","createBy","updateBy","params","dept","roles","children","ancestors","delFlag","admin","flag","avatar","loginDate","loginIp","minioUrl","wxOpenId"})
public record RoleForm(Long roleId, String roleName, String roleKey, Integer roleSort, String dataScope, String status, boolean menuCheckStrictly, boolean deptCheckStrictly, Long[] menuIds, Long[] deptIds, String remark, String kind, String factoryId) {
 public dev.mesbase.common.core.domain.entity.SysRole toEntity(){var result=new dev.mesbase.common.core.domain.entity.SysRole();
result.setRoleId(roleId);
result.setRoleName(roleName);
result.setRoleKey(roleKey);
result.setRoleSort(Objects.toString(roleSort, null));
result.setDataScope(dataScope);
result.setStatus(status);
result.setMenuCheckStrictly(menuCheckStrictly);
result.setDeptCheckStrictly(deptCheckStrictly);
result.setMenuIds(menuIds);
result.setDeptIds(deptIds);
result.setRemark(remark);
result.setKind(kind);
result.setFactoryId(factoryId);
return result;}
}
