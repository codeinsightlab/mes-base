// MES-CODEGEN: scaffold; review required.
package dev.mesbase.business.production.controller.vo;

import java.util.List;

/**
 * 正式生产完工事实分页响应。
 */
public record MesExecutionCompletionPageVo(
        List<MesExecutionCompletionVo> items,
        long total,
        int offset,
        int limit
) {
}

