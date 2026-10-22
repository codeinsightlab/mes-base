package dev.mesbase.web.view.role;

import java.util.Optional;

import java.util.Objects;

import dev.mesbase.common.core.domain.entity.SysRole;
import java.util.Arrays;
import java.util.List;

/** Explicit conversion at the Controller HTTP boundary. */
public final class SysRoleAssembler {
    private SysRoleAssembler() {
    }

    public static SysRoleResult toResult(SysRole row) {
        if (row == null) return null;
        return new SysRoleResult(row.getKind(),
                row.getFactoryId(),
                Objects.toString(row.getRoleId(), null),
                row.getRoleName(),
                row.getRoleKey(),
                Optional.ofNullable(row.getRoleSort()).map(nullableValue -> Integer.valueOf(nullableValue)).orElse(null),
                row.getDataScope(),
                row.isMenuCheckStrictly(),
                row.isDeptCheckStrictly(),
                row.getStatus(),
                row.getDelFlag(),
                row.isFlag(),
                Optional.ofNullable(row.getMenuIds()).map(nullableValue -> Arrays.stream(nullableValue).map(String::valueOf).toArray(String[]::new)).orElse(null),
                Optional.ofNullable(row.getDeptIds()).map(nullableValue -> Arrays.stream(nullableValue).map(String::valueOf).toArray(String[]::new)).orElse(null),
                row.getCreateBy(),
                row.getCreateTime(),
                row.getUpdateBy(),
                row.getUpdateTime(),
                row.getRemark());
    }

    public static List<SysRoleResult> toResults(List<SysRole> rows) {
        if (rows == null) return null;
        return rows.stream().map(SysRoleAssembler::toResult).toList();
    }
}
