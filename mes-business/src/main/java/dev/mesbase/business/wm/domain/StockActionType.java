package dev.mesbase.business.wm.domain;

import java.util.Map;
import java.util.LinkedHashMap;

public enum StockActionType {
    OPENING("期初建账", "OPENING"),
    RECEIPT("原材料入库", "RECEIPT"),
    ISSUE("生产领料", "ISSUE"),
    RETURN("生产退料", "RETURN"),
    GAIN("盘盈", "GAIN"),
    LOSS("盘亏", "LOSS"),
    CORRECTION("业务纠错", "CORRECTION"),
    PRODUCT_RECEIPT("成品仓储接收", "PRODUCT_RECEIPT"),
    PRODUCT_REVERSAL("成品接收逆向", "PRODUCT_REVERSAL"),
    REVERSAL("原操作撤销", "REVERSAL"),
    OTHER_RECEIPT("其他入库", "OTHER_RECEIPT"),
    OTHER_ISSUE("其他出库", "OTHER_ISSUE");
    private final String description;
    private final String value;

    StockActionType(String description, String value) {
        this.description = description;
        this.value = value;
    }

    public static StockActionType from(String value) {
        for (StockActionType item : values()) if (item.value.equals(value)) return item;
        return null;
    }

    public static Map<String, String> toMap() {
        Map<String, String> result = new LinkedHashMap<>();
        for (StockActionType item : values()) result.put(item.value, item.description);
        return result;
    }

    public String getDescription() {
        return description;
    }

    public String getValue() {
        return value;
    }
}
