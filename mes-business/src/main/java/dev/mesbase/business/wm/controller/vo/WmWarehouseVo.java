package dev.mesbase.business.wm.controller.vo;

import java.time.LocalDateTime;
import com.fasterxml.jackson.annotation.JsonProperty;
import dev.mesbase.business.wm.domain.WarehouseType;

/**
 * 仓库管理接口响应白名单。
 */
public record WmWarehouseVo(
        String id,

        String source,
        String code,
        String name,
        String warehouseType,
        String status,
        String remark,
        String createBy,
        String createByName,
        LocalDateTime createTime,
        String updateBy,
        String updateByName,
        LocalDateTime updateTime
) {
    @JsonProperty("warehouseTypeLabel")
    public String warehouseTypeLabel() {
        if (warehouseType == null) return "待补录";
        WarehouseType type = WarehouseType.from(warehouseType);
        if (type == null) return "未知类型";
        return type.getDescription();
    }
}

