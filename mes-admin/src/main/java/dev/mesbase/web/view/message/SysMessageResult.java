package dev.mesbase.web.view.message;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.util.Date;

/** HTTP response whitelist; persistence and credential fields are intentionally absent. */
public record SysMessageResult(String messageId, String messageType, String messageLevel, String messageTitle,
                              String messageContent, String senderId, String senderName, String senderNick,
                              String recipientId, String recipientName, String recipientNick,
                              @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss") Date processTime,
                              String callBack, String status, String remark,
                              @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss") Date createTime) {
}
