// MES-CODEGEN: MyBatis Generator standard single-table persistence.
package dev.mesbase.business.quality.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class MesQualityTreatmentEntity {
    private String id;

    private String requestHash;

    private String declarationId;

    private Integer declarationRevision;

    private Long executionId;

    private Long routingNodeId;

    private String actionType;

    private String parentId;

    private String attributionType;

    private String relatedDeclarationId;

    private String basis;

    private BigDecimal scrapQty;

    private BigDecimal reworkQty;

    private BigDecimal processedQty;

    private BigDecimal recoveredQty;

    private BigDecimal failedQty;

    private Long operatorUserId;

    private Long confirmedBy;

    private LocalDateTime occurredAt;

    private LocalDateTime recordedAt;

    private String reason;

    private Long targetWarehouseId;

    private Long stockDocumentId;

    private BigDecimal transferQty;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getRequestHash() {
        return requestHash;
    }

    public void setRequestHash(String requestHash) {
        this.requestHash = requestHash;
    }

    public String getDeclarationId() {
        return declarationId;
    }

    public void setDeclarationId(String declarationId) {
        this.declarationId = declarationId;
    }

    public Integer getDeclarationRevision() {
        return declarationRevision;
    }

    public void setDeclarationRevision(Integer declarationRevision) {
        this.declarationRevision = declarationRevision;
    }

    public Long getExecutionId() {
        return executionId;
    }

    public void setExecutionId(Long executionId) {
        this.executionId = executionId;
    }

    public Long getRoutingNodeId() {
        return routingNodeId;
    }

    public void setRoutingNodeId(Long routingNodeId) {
        this.routingNodeId = routingNodeId;
    }

    public String getActionType() {
        return actionType;
    }

    public void setActionType(String actionType) {
        this.actionType = actionType;
    }

    public String getParentId() {
        return parentId;
    }

    public void setParentId(String parentId) {
        this.parentId = parentId;
    }

    public String getAttributionType() {
        return attributionType;
    }

    public void setAttributionType(String attributionType) {
        this.attributionType = attributionType;
    }

    public String getRelatedDeclarationId() {
        return relatedDeclarationId;
    }

    public void setRelatedDeclarationId(String relatedDeclarationId) {
        this.relatedDeclarationId = relatedDeclarationId;
    }

    public String getBasis() {
        return basis;
    }

    public void setBasis(String basis) {
        this.basis = basis;
    }

    public BigDecimal getScrapQty() {
        return scrapQty;
    }

    public void setScrapQty(BigDecimal scrapQty) {
        this.scrapQty = scrapQty;
    }

    public BigDecimal getReworkQty() {
        return reworkQty;
    }

    public void setReworkQty(BigDecimal reworkQty) {
        this.reworkQty = reworkQty;
    }

    public BigDecimal getProcessedQty() {
        return processedQty;
    }

    public void setProcessedQty(BigDecimal processedQty) {
        this.processedQty = processedQty;
    }

    public BigDecimal getRecoveredQty() {
        return recoveredQty;
    }

    public void setRecoveredQty(BigDecimal recoveredQty) {
        this.recoveredQty = recoveredQty;
    }

    public BigDecimal getFailedQty() {
        return failedQty;
    }

    public void setFailedQty(BigDecimal failedQty) {
        this.failedQty = failedQty;
    }

    public Long getOperatorUserId() {
        return operatorUserId;
    }

    public void setOperatorUserId(Long operatorUserId) {
        this.operatorUserId = operatorUserId;
    }

    public Long getConfirmedBy() {
        return confirmedBy;
    }

    public void setConfirmedBy(Long confirmedBy) {
        this.confirmedBy = confirmedBy;
    }

    public LocalDateTime getOccurredAt() {
        return occurredAt;
    }

    public void setOccurredAt(LocalDateTime occurredAt) {
        this.occurredAt = occurredAt;
    }

    public LocalDateTime getRecordedAt() {
        return recordedAt;
    }

    public void setRecordedAt(LocalDateTime recordedAt) {
        this.recordedAt = recordedAt;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public Long getTargetWarehouseId() {
        return targetWarehouseId;
    }

    public void setTargetWarehouseId(Long targetWarehouseId) {
        this.targetWarehouseId = targetWarehouseId;
    }

    public Long getStockDocumentId() {
        return stockDocumentId;
    }

    public void setStockDocumentId(Long stockDocumentId) {
        this.stockDocumentId = stockDocumentId;
    }

    public BigDecimal getTransferQty() {
        return transferQty;
    }

    public void setTransferQty(BigDecimal transferQty) {
        this.transferQty = transferQty;
    }
}