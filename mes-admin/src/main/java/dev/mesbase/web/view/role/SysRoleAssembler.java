package dev.mesbase.web.view.role;

import dev.mesbase.common.core.domain.entity.SysRole;
import dev.mesbase.common.utils.bean.BeanConversions;
import java.util.List;

/**
 * Explicit conversion at the Controller HTTP boundary.
 */
public final class SysRoleAssembler {
    private SysRoleAssembler() {
    }

    public static SysRoleResult toResult(SysRole row) {
        return BeanConversions.convert(row, SysRoleResult.class);
    }

    public static List<SysRoleResult> toResults(List<SysRole> rows) {
        if (rows == null) return null;
        return rows.stream().map(SysRoleAssembler::toResult).toList();
    }
}
