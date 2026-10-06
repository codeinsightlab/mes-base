package dev.mesbase.web.view.menu;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.util.Date;
import java.util.List;

/** HTTP response whitelist; persistence and credential fields are intentionally absent. */
public record SysMenuResult(String menuId, String menuName, String parentName, String parentId, Integer orderNum,
                              String path, String component, String query, String isFrame, String isCache,
                              String menuType, String visible, String status, String perms, String icon,
                              List<SysMenuResult> children, String createBy,
                              @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss") Date createTime,
                              String updateBy,
                              @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss") Date updateTime,
                              String remark) {
}
