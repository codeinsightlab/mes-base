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

    private String attributionType;

    private String relatedDeclarationId;

    private String basis;

    private BigDecimal returnedQty;

    private Long operatorUserId;


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


    public BigDecimal getReturnedQty() {
        return returnedQty;
    }

    public void setReturnedQty(BigDecimal returnedQty) {
        this.returnedQty = returnedQty;
    }


    public Long getOperatorUserId() {
        return operatorUserId;
    }

    public void setOperatorUserId(Long operatorUserId) {
        this.operatorUserId = operatorUserId;
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