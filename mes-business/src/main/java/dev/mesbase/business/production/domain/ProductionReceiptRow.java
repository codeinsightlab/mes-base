package dev.mesbase.business.production.domain;

/**
 * 当前引用名称仅用于显示，不冒充历史快照。
 */
public class ProductionReceiptRow extends MesProductionReceiptEntity {
    private String workorderCode;
    private String productCode;
    private String productName;
    private String warehouseName;

    public String getWorkorderCode() {
        return workorderCode;
    }

    public void setWorkorderCode(String v) {
        workorderCode = v;
    }

    public String getProductCode() {
        return productCode;
    }

    public void setProductCode(String v) {
        productCode = v;
    }

    public String getProductName() {
        return productName;
    }

    public void setProductName(String v) {
        productName = v;
    }

    public String getWarehouseName() {
        return warehouseName;
    }

    public void setWarehouseName(String v) {
        warehouseName = v;
    }
}
