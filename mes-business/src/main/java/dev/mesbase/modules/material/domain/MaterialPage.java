// MES-CODEGEN: scaffold; review required. RuoYi template pipeline adapted under MIT.
package dev.mesbase.modules.material.domain;

import java.util.List;

/**
 * 物料产品查询结果，不包含 HTTP 展示转换。
 */
public record MaterialPage(
        List<MaterialEntity> items,
        long total,
        int offset,
        int limit
) {
}

