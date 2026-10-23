// MES-CODEGEN: scaffold; review required.
package dev.mesbase.business.basic.controller.vo;

import dev.mesbase.business.basic.controller.vo.MaterialBomItemVo;
import dev.mesbase.common.core.page.PageData;
import java.util.List;

/**
 * 组成组件分页响应。
 */
public record MaterialBomItemPageVo(
        List<MaterialBomItemVo> items,
        long total,
        int offset,
        int limit
) implements PageData<MaterialBomItemVo> {
}
