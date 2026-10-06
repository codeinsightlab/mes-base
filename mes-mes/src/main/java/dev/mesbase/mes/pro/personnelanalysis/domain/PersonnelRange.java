package dev.mesbase.mes.pro.personnelanalysis.domain;

import dev.mesbase.common.exception.ServiceException;

public enum PersonnelRange {
    THREE_MONTHS("3M", "最近3个月", 3), SIX_MONTHS("6M", "最近6个月", 6),
    TWELVE_MONTHS("12M", "最近12个月", 12), ALL("ALL", "全部保留历史", 0),
    CUSTOM("CUSTOM", "自定义", 0);

    private final String code;
    private final String name;
    private final int months;
    PersonnelRange(String code, String name, int months) {
        this.code = code; this.name = name; this.months = months;
    }
    public String getCode() { return code; }
    public String getName() { return name; }
    public int getMonths() { return months; }
    public static PersonnelRange fromCode(String code) {
        if (code == null || code.isEmpty()) return TWELVE_MONTHS;
        for (PersonnelRange value : values()) if (value.code.equals(code)) return value;
        throw new ServiceException("不支持的统计时间范围");
    }
}
