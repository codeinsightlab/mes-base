// MES-CODEGEN: MyBatis Generator standard single-table persistence.
package dev.mesbase.business.production.domain;

import java.math.BigDecimal;

public class MesProductionCombinationItemEntity {
    private Long id;

    private Long combinationId;

    private Long demandLineId;

    private Integer lineNo;

    private Long materialId;

    private String materialCode;

    private Long unitId;

    private BigDecimal requiredQty;

    private String batchNo;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getCombinationId() {
        return combinationId;
    }

    public void setCombinationId(Long combinationId) {
        this.combinationId = combinationId;
    }

    public Long getDemandLineId() {
        return demandLineId;
    }

    public void setDemandLineId(Long demandLineId) {
        this.demandLineId = demandLineId;
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

    public String getMaterialCode() {
        return materialCode;
    }

    public void setMaterialCode(String materialCode) {
        this.materialCode = materialCode;
    }

    public Long getUnitId() {
        return unitId;
    }

    public void setUnitId(Long unitId) {
        this.unitId = unitId;
    }

    public BigDecimal getRequiredQty() {
        return requiredQty;
    }

    public void setRequiredQty(BigDecimal requiredQty) {
        this.requiredQty = requiredQty;
    }

    public String getBatchNo() {
        return batchNo;
    }

    public void setBatchNo(String batchNo) {
        this.batchNo = batchNo;
    }
}