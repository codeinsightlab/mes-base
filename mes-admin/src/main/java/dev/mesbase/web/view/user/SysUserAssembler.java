package dev.mesbase.web.view.user;

import dev.mesbase.common.core.domain.entity.SysUser;
import dev.mesbase.web.view.dept.SysDeptAssembler;
import dev.mesbase.web.view.role.SysRoleAssembler;
import java.util.Arrays;
import java.util.List;

/** Explicit conversion at the Controller HTTP boundary. */
public final class SysUserAssembler {
    private SysUserAssembler() {
    }

    public static SysUserResult toResult(SysUser row) {
        if (row == null) return null;
        return new SysUserResult(row.getUserId() == null ? null : row.getUserId().toString(),
                row.getDeptId() == null ? null : row.getDeptId().toString(),
                row.getUserName(),
                row.getNickName(),
                row.getUserType(),
                row.getEmail(),
                row.getPhonenumber(),
                row.getSex(),
                row.getAvatar(),
                row.getStatus(),
                row.getDelFlag(),
                row.getLoginIp(),
                row.getLoginDate(),
                SysDeptAssembler.toResult(row.getDept()),
                SysRoleAssembler.toResults(row.getRoles()),
                row.getRoleIds() == null ? null : Arrays.stream(row.getRoleIds()).map(String::valueOf).toArray(String[]::new),
                row.getPostIds() == null ? null : Arrays.stream(row.getPostIds()).map(String::valueOf).toArray(String[]::new),
                row.getRoleId() == null ? null : row.getRoleId().toString(),
                row.getCreateBy(),
                row.getCreateTime(),
                row.getUpdateBy(),
                row.getUpdateTime(),
                row.getRemark());
    }

    public static List<SysUserResult> toResults(List<SysUser> rows) {
        return rows == null ? null : rows.stream().map(SysUserAssembler::toResult).toList();
    }
}
