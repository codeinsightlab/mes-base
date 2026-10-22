package dev.mesbase.business.production.domain;

import dev.mesbase.common.core.page.PageData;
import java.util.List;

/**
 * 工序能力人员查询结果，不包含 HTTP 展示转换。
 */
public record MesOperationUserPage(
        List<MesOperationUserEntity> items,
        long total,
        int offset,
        int limit
) implements PageData<MesOperationUserEntity> {
}

