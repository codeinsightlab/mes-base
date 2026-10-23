// MES-CODEGEN: scaffold; review required.
package dev.mesbase.business.basic.controller.vo;

import dev.mesbase.business.basic.controller.vo.MaterialBomVo;
import dev.mesbase.common.core.page.PageData;
import java.util.List;

/**
 * 物料组成版本分页响应。
 */
public record MaterialBomPageVo(
        List<MaterialBomVo> items,
        long total,
        int offset,
        int limit
) implements PageData<MaterialBomVo> {
}
