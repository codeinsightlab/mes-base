package dev.mesbase.web.view.menu;

import dev.mesbase.common.utils.bean.BeanConversions;
import java.util.Objects;

import dev.mesbase.common.core.domain.entity.SysMenu;
import java.util.List;

/** Explicit conversion at the Controller HTTP boundary. */
public final class SysMenuAssembler {
    private SysMenuAssembler() {
    }

    public static SysMenuResult toResult(SysMenu row) {
        return BeanConversions.convert(row, SysMenuResult.class);
    }

    public static List<SysMenuResult> toResults(List<SysMenu> rows) {
        if (rows == null) return null;
        return rows.stream().map(SysMenuAssembler::toResult).toList();
    }
}
