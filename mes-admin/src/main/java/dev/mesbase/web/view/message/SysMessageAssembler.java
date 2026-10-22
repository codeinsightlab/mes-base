package dev.mesbase.web.view.message;

import dev.mesbase.common.utils.bean.BeanConversions;
import dev.mesbase.system.domain.SysMessage;
import java.util.List;

/** Explicit conversion at the Controller HTTP boundary. */
public final class SysMessageAssembler {
    private SysMessageAssembler() {
    }

    public static SysMessageResult toResult(SysMessage r) {
        return BeanConversions.convert(r, SysMessageResult.class);
    }

    public static List<SysMessageResult> toResults(List<SysMessage> rows) {
        if (rows == null) return null;
        return rows.stream().map(SysMessageAssembler::toResult).toList();
    }
}
