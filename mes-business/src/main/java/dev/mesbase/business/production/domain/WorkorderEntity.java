package dev.mesbase.business.production.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class WorkorderEntity {
    private Long id;

    private String code;

    private Long productId;

    private BigDecimal plannedQty;

    private Long routingId;

    private String status;

    private LocalDateTime plannedStartAt;

    private LocalDateTime plannedEndAt;

    private String remark;

    private Integer revision;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    private String source;

    private Long bomId;

    private Long bomRevision;

    private Long materialRevision;

    private String materialWarehouseIds;

    private String materialWorkCenter;

    private String materialStatus;

    private LocalDateTime materialFrozenAt;

    private Long materialUpdatedBy;

    private LocalDateTime materialUpdatedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public Long getProductId() {
        return productId;
    }

    public void setProductId(Long productId) {
        this.productId = productId;
    }

    public BigDecimal getPlannedQty() {
        return plannedQty;
    }

    public void setPlannedQty(BigDecimal plannedQty) {
        this.plannedQty = plannedQty;
    }

    public Long getRoutingId() {
        return routingId;
    }

    public void setRoutingId(Long routingId) {
        this.routingId = routingId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDateTime getPlannedStartAt() {
        return plannedStartAt;
    }

    public void setPlannedStartAt(LocalDateTime plannedStartAt) {
        this.plannedStartAt = plannedStartAt;
    }

    public LocalDateTime getPlannedEndAt() {
        return plannedEndAt;
    }

    public void setPlannedEndAt(LocalDateTime plannedEndAt) {
        this.plannedEndAt = plannedEndAt;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }

    public Integer getRevision() {
        return revision;
    }

    public void setRevision(Integer revision) {
        this.revision = revision;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }

    public LocalDateTime getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(LocalDateTime updateTime) {
        this.updateTime = updateTime;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public Long getBomId() {
        return bomId;
    }

    public void setBomId(Long bomId) {
        this.bomId = bomId;
    }

    public Long getBomRevision() {
        return bomRevision;
    }

    public void setBomRevision(Long bomRevision) {
        this.bomRevision = bomRevision;
    }

    public Long getMaterialRevision() {
        return materialRevision;
    }

    public void setMaterialRevision(Long materialRevision) {
        this.materialRevision = materialRevision;
    }

    public String getMaterialWarehouseIds() {
        return materialWarehouseIds;
    }

    public void setMaterialWarehouseIds(String materialWarehouseIds) {
        this.materialWarehouseIds = materialWarehouseIds;
    }

    public String getMaterialWorkCenter() {
        return materialWorkCenter;
    }

    public void setMaterialWorkCenter(String materialWorkCenter) {
        this.materialWorkCenter = materialWorkCenter;
    }

    public String getMaterialStatus() {
        return materialStatus;
    }

    public void setMaterialStatus(String materialStatus) {
        this.materialStatus = materialStatus;
    }

    public LocalDateTime getMaterialFrozenAt() {
        return materialFrozenAt;
    }

    public void setMaterialFrozenAt(LocalDateTime materialFrozenAt) {
        this.materialFrozenAt = materialFrozenAt;
    }

    public Long getMaterialUpdatedBy() {
        return materialUpdatedBy;
    }

    public void setMaterialUpdatedBy(Long materialUpdatedBy) {
        this.materialUpdatedBy = materialUpdatedBy;
    }

    public LocalDateTime getMaterialUpdatedAt() {
        return materialUpdatedAt;
    }

    public void setMaterialUpdatedAt(LocalDateTime materialUpdatedAt) {
        this.materialUpdatedAt = materialUpdatedAt;
    }
    private String externalId;
    private String externalCode;

    public String getExternalId() {
        return externalId;
    }

    public void setExternalId(String externalId) {
        this.externalId = externalId;
    }

    public String getExternalCode() {
        return externalCode;
    }

    public void setExternalCode(String externalCode) {
        this.externalCode = externalCode;
    }

}
