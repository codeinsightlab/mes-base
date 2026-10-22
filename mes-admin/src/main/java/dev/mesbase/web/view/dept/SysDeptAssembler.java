package dev.mesbase.web.view.dept;

import java.util.Optional;

import java.util.Objects;

import dev.mesbase.common.core.domain.entity.SysDept;
import java.util.List;

/** Explicit conversion at the Controller HTTP boundary. */
public final class SysDeptAssembler {
    private SysDeptAssembler() {
    }

    public static SysDeptResult toResult(SysDept row) {
        if (row == null) return null;
        return new SysDeptResult(Objects.toString(row.getDeptId(), null),
                Objects.toString(row.getParentId(), null),
                row.getAncestors(),
                row.getDeptName(),
                Optional.ofNullable(row.getOrderNum()).map(nullableValue -> Integer.valueOf(nullableValue)).orElse(null),
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
        if (rows == null) return null;
        return rows.stream().map(SysDeptAssembler::toResult).toList();
    }
}
