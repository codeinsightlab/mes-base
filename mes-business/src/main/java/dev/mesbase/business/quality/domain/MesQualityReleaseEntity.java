package dev.mesbase.business.quality.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class MesQualityReleaseEntity {
    private String id;

    private String executionCode;

    private Long sequenceNo;

    private String actionType;

    private BigDecimal quantity;

    private String basisHash;

    private String referenceId;

    private String requestHash;

    private Long operatorUserId;

    private LocalDateTime recordedAt;

    private String reason;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getExecutionCode() {
        return executionCode;
    }

    public void setExecutionCode(String executionCode) {
        this.executionCode = executionCode;
    }

    public Long getSequenceNo() {
        return sequenceNo;
    }

    public void setSequenceNo(Long sequenceNo) {
        this.sequenceNo = sequenceNo;
    }

    public String getActionType() {
        return actionType;
    }

    public void setActionType(String actionType) {
        this.actionType = actionType;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public void setQuantity(BigDecimal quantity) {
        this.quantity = quantity;
    }

    public String getBasisHash() {
        return basisHash;
    }

    public void setBasisHash(String basisHash) {
        this.basisHash = basisHash;
    }

    public String getReferenceId() {
        return referenceId;
    }

    public void setReferenceId(String referenceId) {
        this.referenceId = referenceId;
    }

    public String getRequestHash() {
        return requestHash;
    }

    public void setRequestHash(String requestHash) {
        this.requestHash = requestHash;
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
}