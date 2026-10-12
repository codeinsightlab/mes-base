// MES-CODEGEN: scaffold; review required. RuoYi template pipeline adapted under MIT.
package dev.mesbase.modules.dv.controller.vo;

import java.util.List;

/**
 * 设备点检保养项目分页响应。
 */
public record DvSubjectPageVo(
        List<DvSubjectVo> items,
        long total,
        int offset,
        int limit
) {
}

