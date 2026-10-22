package dev.mesbase.business.production.controller.vo;

import dev.mesbase.common.core.page.PageData;
import java.util.List;

/**
 * 工序能力人员分页响应。
 */
public record MesOperationUserPageVo(
        List<MesOperationUserVo> items,
        long total,
        int offset,
        int limit
) implements PageData<MesOperationUserVo> {
}

