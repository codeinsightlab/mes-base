package dev.mesbase.system.dto;
@com.fasterxml.jackson.annotation.JsonIgnoreProperties({"createTime","updateTime","createBy","updateBy","params","dept","roles","children","ancestors","parentName","delFlag","admin","flag","avatar","loginDate","loginIp","minioUrl","wxOpenId"})
public record MenuForm(Long menuId, Long parentId, String menuName, Integer orderNum, String path, String component, String query, String isFrame, String isCache, String menuType, String visible, String status, String perms, String icon, String remark) {
 public dev.mesbase.common.core.domain.entity.SysMenu toEntity(){var result=new dev.mesbase.common.core.domain.entity.SysMenu();
result.setMenuId(menuId);
result.setParentId(parentId);
result.setMenuName(menuName);
result.setOrderNum(orderNum);
result.setPath(path);
result.setComponent(component);
result.setQuery(query);
result.setIsFrame(isFrame);
result.setIsCache(isCache);
result.setMenuType(menuType);
result.setVisible(visible);
result.setStatus(status);
result.setPerms(perms);
result.setIcon(icon);
result.setRemark(remark);
return result;}
}
