// MES-CODEGEN: scaffold; review required. RuoYi template pipeline adapted under MIT.
package dev.mesbase.modules.acceptance.controller.vo;

import java.util.List;

/**
 * 人员分页响应。
 */
public record PersonPageVo(
        List<PersonVo> items,
        long total,
        int offset,
        int limit
) {
}

