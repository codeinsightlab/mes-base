package dev.mesbase.web.view.dept;

import dev.mesbase.common.utils.bean.BeanConversions;
import java.util.Optional;

import java.util.Objects;

import dev.mesbase.common.core.domain.entity.SysDept;
import java.util.List;

/** Explicit conversion at the Controller HTTP boundary. */
public final class SysDeptAssembler {
    private SysDeptAssembler() {
    }

    public static SysDeptResult toResult(SysDept row) {
        return BeanConversions.convert(row, SysDeptResult.class);
    }

    public static List<SysDeptResult> toResults(List<SysDept> rows) {
        if (rows == null) return null;
        return rows.stream().map(SysDeptAssembler::toResult).toList();
    }
}
