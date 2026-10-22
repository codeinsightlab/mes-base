package dev.mesbase.web.view.user;

import dev.mesbase.common.core.domain.entity.SysUser;
import dev.mesbase.common.utils.bean.BeanConversions;
import java.util.List;

/**
 * Explicit conversion at the Controller HTTP boundary.
 */
public final class SysUserAssembler {
    private SysUserAssembler() {
    }

    public static SysUserResult toResult(SysUser row) {
        return BeanConversions.convert(row, SysUserResult.class);
    }

    public static List<SysUserResult> toResults(List<SysUser> rows) {
        if (rows == null) return null;
        return rows.stream().map(SysUserAssembler::toResult).toList();
    }
}
