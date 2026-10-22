package dev.mesbase.web.view.log;

import dev.mesbase.common.utils.bean.BeanConversions;
import java.util.Objects;

import dev.mesbase.system.domain.SysOperLog;
import java.util.List;

/** Explicit conversion at the Controller HTTP boundary. */
public final class SysOperLogAssembler {
    private SysOperLogAssembler() {
    }

    public static SysOperLogResult toResult(SysOperLog row) {
        return BeanConversions.convert(row, SysOperLogResult.class);
    }

    public static List<SysOperLogResult> toResults(List<SysOperLog> rows) {
        if (rows == null) return null;
        return rows.stream().map(SysOperLogAssembler::toResult).toList();
    }
}
