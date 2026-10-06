package dev.mesbase.web.view.user;

import com.fasterxml.jackson.annotation.JsonFormat;
import dev.mesbase.web.view.dept.SysDeptResult;
import dev.mesbase.web.view.role.SysRoleResult;
import java.util.Date;
import java.util.List;

/** HTTP response whitelist; persistence and credential fields are intentionally absent. */
public record SysUserResult(String userId, String deptId, String userName, String nickName, String userType,
                              String email, String phonenumber, String sex, String avatar, String status,
                              String delFlag, String loginIp,
                              @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss") Date loginDate,
                              SysDeptResult dept, List<SysRoleResult> roles, String[] roleIds, String[] postIds, String roleId,
                              String createBy,
                              @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss") Date createTime,
                              String updateBy,
                              @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss") Date updateTime,
                              String remark) {
}
