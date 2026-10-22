package dev.mesbase.web.view.log;

import java.util.Objects;

import dev.mesbase.system.domain.SysLogininfor;
import java.util.List;

/** Explicit conversion at the Controller HTTP boundary. */
public final class SysLogininforAssembler {
    private SysLogininforAssembler() {
    }

    public static SysLogininforResult toResult(SysLogininfor row) {
        if (row == null) return null;
        return new SysLogininforResult(row.getRequestId(),
                row.getActorId(),
                Objects.toString(row.getInfoId(), null),
                row.getUserName(),
                row.getStatus(),
                row.getIpaddr(),
                row.getLoginLocation(),
                row.getBrowser(),
                row.getOs(),
                row.getMsg(),
                row.getLoginTime(),
                row.getCreateBy(),
                row.getCreateTime(),
                row.getUpdateBy(),
                row.getUpdateTime(),
                row.getRemark());
    }

    public static List<SysLogininforResult> toResults(List<SysLogininfor> rows) {
        if (rows == null) return null;
        return rows.stream().map(SysLogininforAssembler::toResult).toList();
    }
}
