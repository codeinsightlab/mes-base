package dev.mesbase.business.wm.domain;

/** Stable stock-flow direction persisted on every document. */
public enum StockDocumentDirection {
    IN("IN"), OUT("OUT");

    private final String value;

    StockDocumentDirection(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static StockDocumentDirection from(String value) {
        for (var direction : values()) if (direction.value.equals(value)) return direction;
        return null;
    }
}
