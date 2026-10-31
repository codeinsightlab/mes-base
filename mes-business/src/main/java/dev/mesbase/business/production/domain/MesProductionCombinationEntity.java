// MES-CODEGEN: MyBatis Generator standard single-table persistence.
package dev.mesbase.business.production.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class MesProductionCombinationEntity {
    private Long id;

    private Long workorderId;

    private Long productId;

    private Long outputUnitId;

    private Long demandRevision;

    private BigDecimal plannedQty;

    private String fingerprint;

    private Long executionId;

    private Long createdBy;

    private LocalDateTime createdAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getWorkorderId() {
        return workorderId;
    }

    public void setWorkorderId(Long workorderId) {
        this.workorderId = workorderId;
    }

    public Long getProductId() {
        return productId;
    }

    public void setProductId(Long productId) {
        this.productId = productId;
    }

    public Long getOutputUnitId() {
        return outputUnitId;
    }

    public void setOutputUnitId(Long outputUnitId) {
        this.outputUnitId = outputUnitId;
    }

    public Long getDemandRevision() {
        return demandRevision;
    }

    public void setDemandRevision(Long demandRevision) {
        this.demandRevision = demandRevision;
    }

    public BigDecimal getPlannedQty() {
        return plannedQty;
    }

    public void setPlannedQty(BigDecimal plannedQty) {
        this.plannedQty = plannedQty;
    }

    public String getFingerprint() {
        return fingerprint;
    }

    public void setFingerprint(String fingerprint) {
        this.fingerprint = fingerprint;
    }

    public Long getExecutionId() {
        return executionId;
    }

    public void setExecutionId(Long executionId) {
        this.executionId = executionId;
    }

    public Long getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(Long createdBy) {
        this.createdBy = createdBy;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}