package dev.mesbase.web.view.file;

import java.util.Objects;

import dev.mesbase.system.domain.SysAttachment;
import java.util.List;

/** Explicit conversion at the Controller HTTP boundary. */
public final class SysAttachmentAssembler {
    private SysAttachmentAssembler() {
    }

    public static SysAttachmentResult toResult(SysAttachment r) {
        if (r == null) return null;
        return new SysAttachmentResult(String.valueOf(r.getAttachmentId()),
                Objects.toString(r.getSourceDocId(), null),
                r.getSourceDocType(),
                r.getFileId(),
                r.getFileUrl(),
                r.getFileName(),
                r.getOrignalName(),
                r.getFileType(),
                r.getFileSize(),
                String.valueOf(r.getOwnerId()),
                r.getFactoryId(),
                r.getResource(),
                r.getCreateTime());
    }

    public static List<SysAttachmentResult> toResults(List<SysAttachment> rows) {
        if (rows == null) return null;
        return rows.stream().map(SysAttachmentAssembler::toResult).toList();
    }
}
