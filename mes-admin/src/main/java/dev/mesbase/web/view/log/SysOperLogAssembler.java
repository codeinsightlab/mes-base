package dev.mesbase.web.view.log;

import dev.mesbase.system.domain.SysOperLog;
import java.util.List;

/** Explicit conversion at the Controller HTTP boundary. */
public final class SysOperLogAssembler {
    private SysOperLogAssembler() {
    }

    public static SysOperLogResult toResult(SysOperLog row) {
        if (row == null) return null;
        return new SysOperLogResult(row.getRequestId(),
                row.getActorId(),
                row.getFactoryId(),
                row.getExecutionScope(),
                row.getCostTime(),
                row.getOperId() == null ? null : row.getOperId().toString(),
                row.getTitle(),
                row.getBusinessType(),
                row.getBusinessTypes(),
                row.getMethod(),
                row.getRequestMethod(),
                row.getOperatorType(),
                row.getOperName(),
                row.getDeptName(),
                row.getOperUrl(),
                row.getOperIp(),
                row.getOperLocation(),
                row.getOperParam(),
                row.getJsonResult(),
                row.getStatus(),
                row.getErrorMsg(),
                row.getOperTime(),
                row.getCreateBy(),
                row.getCreateTime(),
                row.getUpdateBy(),
                row.getUpdateTime(),
                row.getRemark());
    }

    public static List<SysOperLogResult> toResults(List<SysOperLog> rows) {
        return rows == null ? null : rows.stream().map(SysOperLogAssembler::toResult).toList();
    }
}
