package dev.mesbase.web.view.file;

import dev.mesbase.common.utils.bean.BeanConversions;
import java.util.Objects;

import dev.mesbase.system.domain.SysAttachment;
import java.util.List;

/** Explicit conversion at the Controller HTTP boundary. */
public final class SysAttachmentAssembler {
    private SysAttachmentAssembler() {
    }

    public static SysAttachmentResult toResult(SysAttachment r) {
        return BeanConversions.convert(r, SysAttachmentResult.class);
    }

    public static List<SysAttachmentResult> toResults(List<SysAttachment> rows) {
        if (rows == null) return null;
        return rows.stream().map(SysAttachmentAssembler::toResult).toList();
    }
}
