package dev.mesbase.web.view.menu;

import java.util.Objects;

import dev.mesbase.common.core.domain.entity.SysMenu;
import java.util.List;

/** Explicit conversion at the Controller HTTP boundary. */
public final class SysMenuAssembler {
    private SysMenuAssembler() {
    }

    public static SysMenuResult toResult(SysMenu row) {
        if (row == null) return null;
        return new SysMenuResult(Objects.toString(row.getMenuId(), null),
                row.getMenuName(),
                row.getParentName(),
                Objects.toString(row.getParentId(), null),
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
        if (rows == null) return null;
        return rows.stream().map(SysMenuAssembler::toResult).toList();
    }
}
