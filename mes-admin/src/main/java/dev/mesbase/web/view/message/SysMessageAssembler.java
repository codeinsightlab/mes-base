package dev.mesbase.web.view.message;

import dev.mesbase.system.domain.SysMessage;
import java.util.List;

/** Explicit conversion at the Controller HTTP boundary. */
public final class SysMessageAssembler {
    private SysMessageAssembler() {
    }

    public static SysMessageResult toResult(SysMessage r) {
        if (r == null) return null;
        return new SysMessageResult(String.valueOf(r.getMessageId()),
                r.getMessageType(),
                r.getMessageLevel(),
                r.getMessageTitle(),
                r.getMessageContent(),
                String.valueOf(r.getSenderId()),
                r.getSenderName(),
                r.getSenderNick(),
                String.valueOf(r.getRecipientId()),
                r.getRecipientName(),
                r.getRecipientNick(),
                r.getProcessTime(),
                r.getCallBack(),
                r.getStatus(),
                r.getRemark(),
                r.getCreateTime());
    }

    public static List<SysMessageResult> toResults(List<SysMessage> rows) {
        if (rows == null) return null;
        return rows.stream().map(SysMessageAssembler::toResult).toList();
    }
}
