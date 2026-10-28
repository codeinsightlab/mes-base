package dev.mesbase.business.wm.domain;

import java.time.LocalDateTime;
import java.math.BigDecimal;

public class WmStockDocumentEntity {
    private Long id;

    private String documentNo;

    private String documentType;

    private String direction;

    private String status;

    private String reason;

    private String remark;

    private Long sourceWorkorderId;

    private String createBy;

    private LocalDateTime createTime;

    private String updateBy;

    private LocalDateTime updateTime;

    private Long postedBy;

    private LocalDateTime postedAt;

    private Long cancelledBy;

    private LocalDateTime cancelledAt;

    private Long revision;

    private String postRequestId;

    private String postRequestHash;

    private Long sourceExecutionId;

    private String productionWorkorderCode;
    private Long productionProductId;
    private String productionProductCode;
    private Long productionRoutingId;

    private String sourceRequestJson;

    private String productionRequestId;
    private String productionRequestHash;
    private String productionInputKind;
    private BigDecimal productionOutputQty;
    private String productionSourceMode;
    private String productionCalculationJson;
    private LocalDateTime productionRequestCreatedAt;
    private LocalDateTime productionConfirmedAt;
    private LocalDateTime productionBusinessOccurredAt;
    private Boolean productionStockApplied;
    private Integer productionPathRevision;
    private String productionConfirmationOperationId;
    private String productionConfirmationStatus;
    private LocalDateTime productionSubmissionCreatedAt;
    private LocalDateTime productionSubmissionFinishedAt;
    private String productionConfirmedPathNodeIds;
    private String productionConfirmationCommandJson;
    private String productionVoidOperationId;
    private String productionVoidFromStatus;
    private LocalDateTime productionVoidRequestedAt;
    private LocalDateTime productionVoidFinishedAt;
    private String productionVoidResultStatus;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getDocumentNo() {
        return documentNo;
    }

    public void setDocumentNo(String documentNo) {
        this.documentNo = documentNo;
    }

    public String getDocumentType() {
        return documentType;
    }

    public void setDocumentType(String documentType) {
        this.documentType = documentType;
    }

    public String getDirection() {
        return direction;
    }

    public void setDirection(String direction) {
        this.direction = direction;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }

    public Long getSourceWorkorderId() {
        return sourceWorkorderId;
    }

    public void setSourceWorkorderId(Long sourceWorkorderId) {
        this.sourceWorkorderId = sourceWorkorderId;
    }

    public String getCreateBy() {
        return createBy;
    }

    public void setCreateBy(String createBy) {
        this.createBy = createBy;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }

    public String getUpdateBy() {
        return updateBy;
    }

    public void setUpdateBy(String updateBy) {
        this.updateBy = updateBy;
    }

    public LocalDateTime getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(LocalDateTime updateTime) {
        this.updateTime = updateTime;
    }

    public Long getPostedBy() {
        return postedBy;
    }

    public void setPostedBy(Long postedBy) {
        this.postedBy = postedBy;
    }

    public LocalDateTime getPostedAt() {
        return postedAt;
    }

    public void setPostedAt(LocalDateTime postedAt) {
        this.postedAt = postedAt;
    }

    public Long getCancelledBy() {
        return cancelledBy;
    }

    public void setCancelledBy(Long cancelledBy) {
        this.cancelledBy = cancelledBy;
    }

    public LocalDateTime getCancelledAt() {
        return cancelledAt;
    }

    public void setCancelledAt(LocalDateTime cancelledAt) {
        this.cancelledAt = cancelledAt;
    }

    public Long getRevision() {
        return revision;
    }

    public void setRevision(Long revision) {
        this.revision = revision;
    }

    public String getPostRequestId() {
        return postRequestId;
    }

    public void setPostRequestId(String postRequestId) {
        this.postRequestId = postRequestId;
    }

    public String getPostRequestHash() {
        return postRequestHash;
    }

    public void setPostRequestHash(String postRequestHash) {
        this.postRequestHash = postRequestHash;
    }

    public Long getSourceExecutionId() {
        return sourceExecutionId;
    }

    public void setSourceExecutionId(Long sourceExecutionId) {
        this.sourceExecutionId = sourceExecutionId;
    }

    public String getProductionWorkorderCode() { return productionWorkorderCode; }
    public void setProductionWorkorderCode(String productionWorkorderCode) { this.productionWorkorderCode = productionWorkorderCode; }
    public Long getProductionProductId() { return productionProductId; }
    public void setProductionProductId(Long productionProductId) { this.productionProductId = productionProductId; }
    public String getProductionProductCode() { return productionProductCode; }
    public void setProductionProductCode(String productionProductCode) { this.productionProductCode = productionProductCode; }
    public Long getProductionRoutingId() { return productionRoutingId; }
    public void setProductionRoutingId(Long productionRoutingId) { this.productionRoutingId = productionRoutingId; }

    public String getSourceRequestJson() {
        return sourceRequestJson;
    }

    public void setSourceRequestJson(String sourceRequestJson) {
        this.sourceRequestJson = sourceRequestJson;
    }

    public String getProductionRequestId() { return productionRequestId; }
    public void setProductionRequestId(String productionRequestId) { this.productionRequestId = productionRequestId; }
    public String getProductionRequestHash() { return productionRequestHash; }
    public void setProductionRequestHash(String productionRequestHash) { this.productionRequestHash = productionRequestHash; }
    public String getProductionInputKind() { return productionInputKind; }
    public void setProductionInputKind(String productionInputKind) { this.productionInputKind = productionInputKind; }
    public BigDecimal getProductionOutputQty() { return productionOutputQty; }
    public void setProductionOutputQty(BigDecimal productionOutputQty) { this.productionOutputQty = productionOutputQty; }
    public String getProductionSourceMode() { return productionSourceMode; }
    public void setProductionSourceMode(String productionSourceMode) { this.productionSourceMode = productionSourceMode; }
    public String getProductionCalculationJson() { return productionCalculationJson; }
    public void setProductionCalculationJson(String productionCalculationJson) { this.productionCalculationJson = productionCalculationJson; }
    public LocalDateTime getProductionRequestCreatedAt() { return productionRequestCreatedAt; }
    public void setProductionRequestCreatedAt(LocalDateTime productionRequestCreatedAt) { this.productionRequestCreatedAt = productionRequestCreatedAt; }
    public LocalDateTime getProductionConfirmedAt() { return productionConfirmedAt; }
    public void setProductionConfirmedAt(LocalDateTime productionConfirmedAt) { this.productionConfirmedAt = productionConfirmedAt; }
    public LocalDateTime getProductionBusinessOccurredAt() { return productionBusinessOccurredAt; }
    public void setProductionBusinessOccurredAt(LocalDateTime productionBusinessOccurredAt) { this.productionBusinessOccurredAt = productionBusinessOccurredAt; }
    public Boolean getProductionStockApplied() { return productionStockApplied; }
    public void setProductionStockApplied(Boolean productionStockApplied) { this.productionStockApplied = productionStockApplied; }
    public Integer getProductionPathRevision() { return productionPathRevision; }
    public void setProductionPathRevision(Integer productionPathRevision) { this.productionPathRevision = productionPathRevision; }
    public String getProductionConfirmationOperationId() { return productionConfirmationOperationId; }
    public void setProductionConfirmationOperationId(String productionConfirmationOperationId) { this.productionConfirmationOperationId = productionConfirmationOperationId; }
    public String getProductionConfirmationStatus() { return productionConfirmationStatus; }
    public void setProductionConfirmationStatus(String productionConfirmationStatus) { this.productionConfirmationStatus = productionConfirmationStatus; }
    public LocalDateTime getProductionSubmissionCreatedAt() { return productionSubmissionCreatedAt; }
    public void setProductionSubmissionCreatedAt(LocalDateTime productionSubmissionCreatedAt) { this.productionSubmissionCreatedAt = productionSubmissionCreatedAt; }
    public LocalDateTime getProductionSubmissionFinishedAt() { return productionSubmissionFinishedAt; }
    public void setProductionSubmissionFinishedAt(LocalDateTime productionSubmissionFinishedAt) { this.productionSubmissionFinishedAt = productionSubmissionFinishedAt; }
    public String getProductionConfirmedPathNodeIds() { return productionConfirmedPathNodeIds; }
    public void setProductionConfirmedPathNodeIds(String productionConfirmedPathNodeIds) { this.productionConfirmedPathNodeIds = productionConfirmedPathNodeIds; }
    public String getProductionConfirmationCommandJson() { return productionConfirmationCommandJson; }
    public void setProductionConfirmationCommandJson(String productionConfirmationCommandJson) { this.productionConfirmationCommandJson = productionConfirmationCommandJson; }
    public String getProductionVoidOperationId() { return productionVoidOperationId; }
    public void setProductionVoidOperationId(String productionVoidOperationId) { this.productionVoidOperationId = productionVoidOperationId; }
    public String getProductionVoidFromStatus() { return productionVoidFromStatus; }
    public void setProductionVoidFromStatus(String productionVoidFromStatus) { this.productionVoidFromStatus = productionVoidFromStatus; }
    public LocalDateTime getProductionVoidRequestedAt() { return productionVoidRequestedAt; }
    public void setProductionVoidRequestedAt(LocalDateTime productionVoidRequestedAt) { this.productionVoidRequestedAt = productionVoidRequestedAt; }
    public LocalDateTime getProductionVoidFinishedAt() { return productionVoidFinishedAt; }
    public void setProductionVoidFinishedAt(LocalDateTime productionVoidFinishedAt) { this.productionVoidFinishedAt = productionVoidFinishedAt; }
    public String getProductionVoidResultStatus() { return productionVoidResultStatus; }
    public void setProductionVoidResultStatus(String productionVoidResultStatus) { this.productionVoidResultStatus = productionVoidResultStatus; }
}
