package dev.mesbase.business.quality.domain;

/**
 * UNKNOWN is a historical read projection only and never a new declaration choice.
 */
public enum DefectCostCategory {
    LABOR, MATERIAL, BOTH, UNKNOWN;

    public static boolean valid(String value) {
        for (var item : values()) if (item != UNKNOWN && item.name().equals(value)) return true;
        return false;
    }
}
