// MES-CODEGEN: scaffold; review required.
package dev.mesbase.business.resource.domain;

import java.util.List;

/**
 * 生产人员查询结果，不包含 HTTP 展示转换。
 */
public record PersonnelPage(
        List<PersonnelEntity> items,
        long total,
        int offset,
        int limit
) {
}

