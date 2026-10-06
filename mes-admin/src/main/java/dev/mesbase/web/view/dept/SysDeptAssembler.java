package dev.mesbase.web.view.dept;

import dev.mesbase.common.core.domain.entity.SysDept;
import java.util.List;

/** Explicit conversion at the Controller HTTP boundary. */
public final class SysDeptAssembler {
    private SysDeptAssembler() {
    }

    public static SysDeptResult toResult(SysDept row) {
        if (row == null) return null;
        return new SysDeptResult(row.getDeptId() == null ? null : row.getDeptId().toString(),
                row.getParentId() == null ? null : row.getParentId().toString(),
                row.getAncestors(),
                row.getDeptName(),
                row.getOrderNum() == null ? null : Integer.valueOf(row.getOrderNum()),
                row.getLeader(),
                row.getPhone(),
                row.getEmail(),
                row.getStatus(),
                row.getDelFlag(),
                row.getParentName(),
                SysDeptAssembler.toResults(row.getChildren()),
                row.getCreateBy(),
                row.getCreateTime(),
                row.getUpdateBy(),
                row.getUpdateTime(),
                row.getRemark());
    }

    public static List<SysDeptResult> toResults(List<SysDept> rows) {
        return rows == null ? null : rows.stream().map(SysDeptAssembler::toResult).toList();
    }
}
