package dev.mesbase.business.basic.domain;

import dev.mesbase.common.core.page.PageData;
import java.util.List;

/**
 * 工作站查询结果，不包含 HTTP 展示转换。
 */
public record WorkstationPage(
        List<WorkstationEntity> items,
        long total,
        int offset,
        int limit
) implements PageData<WorkstationEntity> {
}

