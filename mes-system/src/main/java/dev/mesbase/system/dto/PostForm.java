package dev.mesbase.system.dto;
@com.fasterxml.jackson.annotation.JsonIgnoreProperties({"createTime","updateTime","createBy","updateBy","params","dept","roles","children","ancestors","delFlag","admin","flag","avatar","loginDate","loginIp","minioUrl","wxOpenId"})
public record PostForm(Long postId, String postCode, String postName, Integer postSort, String status, String remark) {
 public dev.mesbase.system.domain.SysPost toEntity(){var result=new dev.mesbase.system.domain.SysPost();
result.setPostId(postId);
result.setPostCode(postCode);
result.setPostName(postName);
result.setPostSort(postSort==null?null:postSort.toString());
result.setStatus(status);
result.setRemark(remark);
return result;}
}
