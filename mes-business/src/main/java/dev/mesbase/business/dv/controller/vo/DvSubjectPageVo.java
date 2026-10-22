package dev.mesbase.business.dv.controller.vo;

import dev.mesbase.common.core.page.PageData;
import java.util.List;

/**
 * 设备点检保养项目分页响应。
 */
public record DvSubjectPageVo(
        List<DvSubjectVo> items,
        long total,
        int offset,
        int limit
) implements PageData<DvSubjectVo> {
}

