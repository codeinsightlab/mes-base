package dev.mesbase.mes.enumeration;
/** SOURCE_PRODUCT_PORT: only the reporting state consumed by the migrated monitor. */
public enum DicEnums {
    MES_FEEDBACK_STATUS_ALL_WAREHOUSING("5");
    private final String value;
    DicEnums(String value){this.value=value;}
    public String getValue(){return value;}
}
