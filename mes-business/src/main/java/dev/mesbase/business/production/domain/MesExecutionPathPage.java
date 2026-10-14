// MES-CODEGEN: scaffold; review required.
package dev.mesbase.business.production.domain;

import java.util.List;

/**
 * 生产执行当前完整路径查询结果，不包含 HTTP 展示转换。
 */
public record MesExecutionPathPage(
        List<MesExecutionPathEntity> items,
        long total,
        int offset,
        int limit
) {
}

