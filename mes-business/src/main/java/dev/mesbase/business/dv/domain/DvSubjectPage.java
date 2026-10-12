// MES-CODEGEN: scaffold; review required.
package dev.mesbase.business.dv.domain;

import java.util.List;

/**
 * 设备点检保养项目查询结果，不包含 HTTP 展示转换。
 */
public record DvSubjectPage(
        List<DvSubjectEntity> items,
        long total,
        int offset,
        int limit
) {
}

