package dev.mesbase.web.view.file;

import dev.mesbase.system.domain.SysAttachment;
import java.util.List;

/** Explicit conversion at the Controller HTTP boundary. */
public final class SysAttachmentAssembler {
    private SysAttachmentAssembler() {
    }

    public static SysAttachmentResult toResult(SysAttachment r) {
        if (r == null) return null;
        return new SysAttachmentResult(String.valueOf(r.getAttachmentId()),
                r.getSourceDocId() == null ? null : r.getSourceDocId().toString(),
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
        return rows == null ? null : rows.stream().map(SysAttachmentAssembler::toResult).toList();
    }
}
