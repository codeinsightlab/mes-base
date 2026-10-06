package dev.mesbase.web.view.role;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.util.Date;

/** HTTP response whitelist; persistence and credential fields are intentionally absent. */
public record SysRoleResult(String kind, String factoryId, String roleId, String roleName, String roleKey,
                              Integer roleSort, String dataScope, boolean menuCheckStrictly, boolean deptCheckStrictly,
                              String status, String delFlag, boolean flag, String[] menuIds, String[] deptIds,
                              String createBy,
                              @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss") Date createTime,
                              String updateBy,
                              @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss") Date updateTime,
                              String remark) {
}
