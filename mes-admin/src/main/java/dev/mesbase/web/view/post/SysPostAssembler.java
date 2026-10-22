package dev.mesbase.web.view.post;

import dev.mesbase.common.utils.bean.BeanConversions;
import java.util.Optional;

import java.util.Objects;

import dev.mesbase.system.domain.SysPost;
import java.util.List;

/** Explicit conversion at the Controller HTTP boundary. */
public final class SysPostAssembler {
    private SysPostAssembler() {
    }

    public static SysPostResult toResult(SysPost row) {
        return BeanConversions.convert(row, SysPostResult.class);
    }

    public static List<SysPostResult> toResults(List<SysPost> rows) {
        if (rows == null) return null;
        return rows.stream().map(SysPostAssembler::toResult).toList();
    }
}
