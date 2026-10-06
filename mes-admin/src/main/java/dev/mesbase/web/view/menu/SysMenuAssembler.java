package dev.mesbase.web.view.menu;

import dev.mesbase.common.core.domain.entity.SysMenu;
import java.util.List;

/** Explicit conversion at the Controller HTTP boundary. */
public final class SysMenuAssembler {
    private SysMenuAssembler() {
    }

    public static SysMenuResult toResult(SysMenu row) {
        if (row == null) return null;
        return new SysMenuResult(row.getMenuId() == null ? null : row.getMenuId().toString(),
                row.getMenuName(),
                row.getParentName(),
                row.getParentId() == null ? null : row.getParentId().toString(),
                row.getOrderNum(),
                row.getPath(),
                row.getComponent(),
                row.getQuery(),
                row.getIsFrame(),
                row.getIsCache(),
                row.getMenuType(),
                row.getVisible(),
                row.getStatus(),
                row.getPerms(),
                row.getIcon(),
                SysMenuAssembler.toResults(row.getChildren()),
                row.getCreateBy(),
                row.getCreateTime(),
                row.getUpdateBy(),
                row.getUpdateTime(),
                row.getRemark());
    }

    public static List<SysMenuResult> toResults(List<SysMenu> rows) {
        return rows == null ? null : rows.stream().map(SysMenuAssembler::toResult).toList();
    }
}
