// MES-CODEGEN: scaffold; review required. RuoYi template pipeline adapted under MIT.
package dev.mesbase.modules.acceptance.domain;

import java.util.List;

/**
 * 人员查询结果，不包含 HTTP 展示转换。
 */
public record PersonPage(
        List<PersonEntity> items,
        long total,
        int offset,
        int limit
) {
}

