package dev.mesbase.web.view.role;

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
                row.getRoleId() == null ? null : row.getRoleId().toString(),
                row.getRoleName(),
                row.getRoleKey(),
                row.getRoleSort() == null ? null : Integer.valueOf(row.getRoleSort()),
                row.getDataScope(),
                row.isMenuCheckStrictly(),
                row.isDeptCheckStrictly(),
                row.getStatus(),
                row.getDelFlag(),
                row.isFlag(),
                row.getMenuIds() == null ? null : Arrays.stream(row.getMenuIds()).map(String::valueOf).toArray(String[]::new),
                row.getDeptIds() == null ? null : Arrays.stream(row.getDeptIds()).map(String::valueOf).toArray(String[]::new),
                row.getCreateBy(),
                row.getCreateTime(),
                row.getUpdateBy(),
                row.getUpdateTime(),
                row.getRemark());
    }

    public static List<SysRoleResult> toResults(List<SysRole> rows) {
        return rows == null ? null : rows.stream().map(SysRoleAssembler::toResult).toList();
    }
}
