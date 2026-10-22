package dev.mesbase.web.view.user;

import java.util.Optional;

import java.util.Objects;

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
        return new SysUserResult(Objects.toString(row.getUserId(), null),
                Objects.toString(row.getDeptId(), null),
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
                Optional.ofNullable(row.getRoleIds()).map(nullableValue -> Arrays.stream(nullableValue).map(String::valueOf).toArray(String[]::new)).orElse(null),
                Optional.ofNullable(row.getPostIds()).map(nullableValue -> Arrays.stream(nullableValue).map(String::valueOf).toArray(String[]::new)).orElse(null),
                Objects.toString(row.getRoleId(), null),
                row.getCreateBy(),
                row.getCreateTime(),
                row.getUpdateBy(),
                row.getUpdateTime(),
                row.getRemark());
    }

    public static List<SysUserResult> toResults(List<SysUser> rows) {
        if (rows == null) return null;
        return rows.stream().map(SysUserAssembler::toResult).toList();
    }
}
