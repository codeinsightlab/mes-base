// MES-CODEGEN: MyBatis Generator standard single-table persistence.
package dev.mesbase.business.production.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class MesExecutionCompletionEntity {
    private String id;

    private Long executionId;

    private BigDecimal effectiveQty;

    private BigDecimal releasedQty;

    private BigDecimal receiptQty;

    private BigDecimal acceptedQty;

    private String basisHash;

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

    public Long getExecutionId() {
        return executionId;
    }

    public void setExecutionId(Long executionId) {
        this.executionId = executionId;
    }

    public BigDecimal getEffectiveQty() {
        return effectiveQty;
    }

    public void setEffectiveQty(BigDecimal effectiveQty) {
        this.effectiveQty = effectiveQty;
    }

    public BigDecimal getReleasedQty() {
        return releasedQty;
    }

    public void setReleasedQty(BigDecimal releasedQty) {
        this.releasedQty = releasedQty;
    }

    public BigDecimal getReceiptQty() {
        return receiptQty;
    }

    public void setReceiptQty(BigDecimal receiptQty) {
        this.receiptQty = receiptQty;
    }

    public BigDecimal getAcceptedQty() {
        return acceptedQty;
    }

    public void setAcceptedQty(BigDecimal acceptedQty) {
        this.acceptedQty = acceptedQty;
    }

    public String getBasisHash() {
        return basisHash;
    }

    public void setBasisHash(String basisHash) {
        this.basisHash = basisHash;
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