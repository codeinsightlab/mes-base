package dev.mesbase.web.view.log;

import dev.mesbase.common.utils.bean.BeanConversions;
import java.util.Objects;

import dev.mesbase.system.domain.SysLogininfor;
import java.util.List;

/** Explicit conversion at the Controller HTTP boundary. */
public final class SysLogininforAssembler {
    private SysLogininforAssembler() {
    }

    public static SysLogininforResult toResult(SysLogininfor row) {
        return BeanConversions.convert(row, SysLogininforResult.class);
    }

    public static List<SysLogininforResult> toResults(List<SysLogininfor> rows) {
        if (rows == null) return null;
        return rows.stream().map(SysLogininforAssembler::toResult).toList();
    }
}
