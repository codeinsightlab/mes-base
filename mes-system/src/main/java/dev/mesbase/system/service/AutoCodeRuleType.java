package dev.mesbase.system.service;

/** Fixed business categories exposed by document creation flows and the matching system dictionary. */
public enum AutoCodeRuleType {
    WORK_ORDER,
    STOCK_OUT,
    STOCK_IN;

    public static boolean supports(String value) {
        if (value == null) return false;
        for (AutoCodeRuleType type : values()) if (type.name().equals(value)) return true;
        return false;
    }
}
