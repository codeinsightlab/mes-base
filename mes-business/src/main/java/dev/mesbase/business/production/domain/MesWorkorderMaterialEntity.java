// MES-CODEGEN: MyBatis Generator standard single-table persistence.
package dev.mesbase.business.production.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class MesWorkorderMaterialEntity {
    private Long id;

    private Long materialId;

    private Long unitId;

    private BigDecimal totalRequiredQty;

    private BigDecimal plannedQty;

    private String warehouseIds;

    private String workCenter;

    private Long revision;

    private String status;

    private Long updatedBy;

    private LocalDateTime updatedAt;

    private Long bomId;

    private BigDecimal bomBaseQty;

    private BigDecimal bomComponentQty;

    private Long outputUnitId;

    private LocalDateTime frozenAt;

    private Long workorderId;

    private Integer lineNo;

    private Long bomItemId;

    private Boolean manuallyAdjusted;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getMaterialId() {
        return materialId;
    }

    public void setMaterialId(Long materialId) {
        this.materialId = materialId;
    }

    public Long getUnitId() {
        return unitId;
    }

    public void setUnitId(Long unitId) {
        this.unitId = unitId;
    }

    public BigDecimal getTotalRequiredQty() {
        return totalRequiredQty;
    }

    public void setTotalRequiredQty(BigDecimal totalRequiredQty) {
        this.totalRequiredQty = totalRequiredQty;
    }

    public BigDecimal getPlannedQty() {
        return plannedQty;
    }

    public void setPlannedQty(BigDecimal plannedQty) {
        this.plannedQty = plannedQty;
    }

    public String getWarehouseIds() {
        return warehouseIds;
    }

    public void setWarehouseIds(String warehouseIds) {
        this.warehouseIds = warehouseIds;
    }

    public String getWorkCenter() {
        return workCenter;
    }

    public void setWorkCenter(String workCenter) {
        this.workCenter = workCenter;
    }

    public Long getRevision() {
        return revision;
    }

    public void setRevision(Long revision) {
        this.revision = revision;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Long getUpdatedBy() {
        return updatedBy;
    }

    public void setUpdatedBy(Long updatedBy) {
        this.updatedBy = updatedBy;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public Long getBomId() {
        return bomId;
    }

    public void setBomId(Long bomId) {
        this.bomId = bomId;
    }

    public BigDecimal getBomBaseQty() {
        return bomBaseQty;
    }

    public void setBomBaseQty(BigDecimal bomBaseQty) {
        this.bomBaseQty = bomBaseQty;
    }

    public BigDecimal getBomComponentQty() {
        return bomComponentQty;
    }

    public void setBomComponentQty(BigDecimal bomComponentQty) {
        this.bomComponentQty = bomComponentQty;
    }

    public Long getOutputUnitId() {
        return outputUnitId;
    }

    public void setOutputUnitId(Long outputUnitId) {
        this.outputUnitId = outputUnitId;
    }

    public LocalDateTime getFrozenAt() {
        return frozenAt;
    }

    public void setFrozenAt(LocalDateTime frozenAt) {
        this.frozenAt = frozenAt;
    }

    public Long getWorkorderId() {
        return workorderId;
    }

    public void setWorkorderId(Long workorderId) {
        this.workorderId = workorderId;
    }

    public Integer getLineNo() {
        return lineNo;
    }

    public void setLineNo(Integer lineNo) {
        this.lineNo = lineNo;
    }

    public Long getBomItemId() {
        return bomItemId;
    }

    public void setBomItemId(Long bomItemId) {
        this.bomItemId = bomItemId;
    }

    public Boolean getManuallyAdjusted() {
        return manuallyAdjusted;
    }

    public void setManuallyAdjusted(Boolean manuallyAdjusted) {
        this.manuallyAdjusted = manuallyAdjusted;
    }
}