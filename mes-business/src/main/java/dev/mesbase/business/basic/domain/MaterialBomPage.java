// MES-CODEGEN: scaffold; review required.
package dev.mesbase.business.basic.domain;

import dev.mesbase.business.basic.domain.MaterialBomEntity;
import dev.mesbase.common.core.page.PageData;
import java.util.List;

/**
 * 物料组成版本查询结果，不包含 HTTP 展示转换。
 */
public record MaterialBomPage(
        List<MaterialBomEntity> items,
        long total,
        int offset,
        int limit
) implements PageData<MaterialBomEntity> {
}
