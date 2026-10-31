// MES-CODEGEN: MyBatis Generator standard single-table persistence.
package dev.mesbase.business.wm.domain;

import java.math.BigDecimal;

public class WmStockDocumentLineEntity {
    private Long id;

    private Long documentId;

    private Integer lineNo;

    private Long materialId;

    private Long warehouseId;

    private Long stockId;

    private String batchNo;

    private String binCode;

    private Long unitId;

    private BigDecimal quantity;

    private String remark;

    private String stockActionId;

    private Long revision;

    private Long demandLineId;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getDocumentId() {
        return documentId;
    }

    public void setDocumentId(Long documentId) {
        this.documentId = documentId;
    }

    public Integer getLineNo() {
        return lineNo;
    }

    public void setLineNo(Integer lineNo) {
        this.lineNo = lineNo;
    }

    public Long getMaterialId() {
        return materialId;
    }

    public void setMaterialId(Long materialId) {
        this.materialId = materialId;
    }

    public Long getWarehouseId() {
        return warehouseId;
    }

    public void setWarehouseId(Long warehouseId) {
        this.warehouseId = warehouseId;
    }

    public Long getStockId() {
        return stockId;
    }

    public void setStockId(Long stockId) {
        this.stockId = stockId;
    }

    public String getBatchNo() {
        return batchNo;
    }

    public void setBatchNo(String batchNo) {
        this.batchNo = batchNo;
    }

    public String getBinCode() {
        return binCode;
    }

    public void setBinCode(String binCode) {
        this.binCode = binCode;
    }

    public Long getUnitId() {
        return unitId;
    }

    public void setUnitId(Long unitId) {
        this.unitId = unitId;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public void setQuantity(BigDecimal quantity) {
        this.quantity = quantity;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }

    public String getStockActionId() {
        return stockActionId;
    }

    public void setStockActionId(String stockActionId) {
        this.stockActionId = stockActionId;
    }

    public Long getRevision() {
        return revision;
    }

    public void setRevision(Long revision) {
        this.revision = revision;
    }

    public Long getDemandLineId() {
        return demandLineId;
    }

    public void setDemandLineId(Long demandLineId) {
        this.demandLineId = demandLineId;
    }
}