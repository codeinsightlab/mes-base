package dev.mesbase.business.basic.controller.vo;

import dev.mesbase.common.core.page.PageData;
import java.util.List;

/**
 * 物料产品分页响应。
 */
public record MaterialPageVo(
        List<MaterialVo> items,
        long total,
        int offset,
        int limit
) implements PageData<MaterialVo> {
}

