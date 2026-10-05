package dev.mesbase.system.dto;
@com.fasterxml.jackson.annotation.JsonIgnoreProperties({"createTime","updateTime","createBy","updateBy","params","dept","roles","roleId","children","ancestors","delFlag","admin","flag","avatar","loginDate","loginIp","minioUrl","wxOpenId"})
public record UserForm(Long userId, Long deptId, String userName, String nickName, String userType, String email, String phonenumber, String sex, String status, String password, Long[] roleIds, Long[] postIds, String remark) {
 public dev.mesbase.common.core.domain.entity.SysUser toEntity(){var result=new dev.mesbase.common.core.domain.entity.SysUser();
result.setUserId(userId);
result.setDeptId(deptId);
result.setUserName(userName);
result.setNickName(nickName);
result.setUserType(userType);
result.setEmail(email);
result.setPhonenumber(phonenumber);
result.setSex(sex);
result.setStatus(status);
result.setPassword(password);
result.setRoleIds(roleIds);
result.setPostIds(postIds);
result.setRemark(remark);
return result;}
}
