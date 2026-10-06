package dev.mesbase.web.view.file;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.math.BigDecimal;
import java.util.Date;

/** HTTP response whitelist; persistence and credential fields are intentionally absent. */
public record SysAttachmentResult(String attachmentId, String sourceDocId, String sourceDocType, String fileId,
                                 String fileUrl, String fileName, String orignalName, String fileType,
                                 BigDecimal fileSize, String ownerId, String factoryId, String resource,
                                 @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss") Date createTime) {
}
