package dev.mesbase.business.purchase.domain;

/**
 * Current display names joined in one bounded query; never used as purchase document snapshots.
 */
public class PurchaseReferencePriceRow extends PurchaseReferencePriceEntity {
    private String supplierCode;
    private String supplierName;
    private String materialCode;
    private String materialName;
    private String unitName;

    public String getSupplierCode() {
        return supplierCode;
    }

    public void setSupplierCode(String value) {
        supplierCode = value;
    }

    public String getSupplierName() {
        return supplierName;
    }

    public void setSupplierName(String value) {
        supplierName = value;
    }

    public String getMaterialCode() {
        return materialCode;
    }

    public void setMaterialCode(String value) {
        materialCode = value;
    }

    public String getMaterialName() {
        return materialName;
    }

    public void setMaterialName(String value) {
        materialName = value;
    }

    public String getUnitName() {
        return unitName;
    }

    public void setUnitName(String value) {
        unitName = value;
    }
}
