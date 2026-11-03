package dev.mesbase.business.production.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class MesProductionReceiptEntity {
    private String id;

    private String receiptNo;

    private String executionCode;

    private String productLotCode;

    private Long warehouseId;

    private String recordType;

    private BigDecimal quantityDelta;

    private String referenceReceiptId;

    private Long operatorUserId;

    private Long recordedBy;

    private LocalDateTime occurredAt;

    private LocalDateTime createTime;

    private String reason;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getReceiptNo() {
        return receiptNo;
    }

    public void setReceiptNo(String receiptNo) {
        this.receiptNo = receiptNo;
    }

    public String getExecutionCode() {
        return executionCode;
    }

    public void setExecutionCode(String executionCode) {
        this.executionCode = executionCode;
    }

    public String getProductLotCode() {
        return productLotCode;
    }

    public void setProductLotCode(String productLotCode) {
        this.productLotCode = productLotCode;
    }

    public Long getWarehouseId() {
        return warehouseId;
    }

    public void setWarehouseId(Long warehouseId) {
        this.warehouseId = warehouseId;
    }

    public String getRecordType() {
        return recordType;
    }

    public void setRecordType(String recordType) {
        this.recordType = recordType;
    }

    public BigDecimal getQuantityDelta() {
        return quantityDelta;
    }

    public void setQuantityDelta(BigDecimal quantityDelta) {
        this.quantityDelta = quantityDelta;
    }

    public String getReferenceReceiptId() {
        return referenceReceiptId;
    }

    public void setReferenceReceiptId(String referenceReceiptId) {
        this.referenceReceiptId = referenceReceiptId;
    }

    public Long getOperatorUserId() {
        return operatorUserId;
    }

    public void setOperatorUserId(Long operatorUserId) {
        this.operatorUserId = operatorUserId;
    }

    public Long getRecordedBy() {
        return recordedBy;
    }

    public void setRecordedBy(Long recordedBy) {
        this.recordedBy = recordedBy;
    }

    public LocalDateTime getOccurredAt() {
        return occurredAt;
    }

    public void setOccurredAt(LocalDateTime occurredAt) {
        this.occurredAt = occurredAt;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }
}