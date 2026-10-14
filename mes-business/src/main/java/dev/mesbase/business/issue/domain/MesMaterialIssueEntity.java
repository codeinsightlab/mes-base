package dev.mesbase.business.issue.domain;

import java.time.LocalDateTime;

public class MesMaterialIssueEntity {
    // ERP compatibility projections loaded from Extra by the business service; not main-table columns.
    private Long id;

    private String issueNo;

    private Long workorderId;

    private Long executionId;

    private String workorderCode;

    private Long productId;

    private String productCode;

    private String flowCardNo;

    private Long routingId;

    private LocalDateTime issueTime;

    private String status;

    private String remark;

    private String createBy;

    private LocalDateTime createTime;

    private String updateBy;

    private LocalDateTime updateTime;

    private String sourceExecutionMode;

    private String externalEffect;

    private String externalBindingKey;

    private String externalReference;

    private String voidOperationId;

    private String voidFromStatus;

    private String voidResultStatus;

    private String voidExecutionMode;

    private LocalDateTime voidRequestedAt;

    private LocalDateTime voidFinishedAt;

    private String voidMessage;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getIssueNo() {
        return issueNo;
    }

    public void setIssueNo(String issueNo) {
        this.issueNo = issueNo;
    }

    public Long getWorkorderId() {
        return workorderId;
    }

    public void setWorkorderId(Long workorderId) {
        this.workorderId = workorderId;
    }

    public Long getExecutionId() {
        return executionId;
    }

    public void setExecutionId(Long executionId) {
        this.executionId = executionId;
    }

    public String getWorkorderCode() {
        return workorderCode;
    }

    public void setWorkorderCode(String workorderCode) {
        this.workorderCode = workorderCode;
    }

    public Long getProductId() {
        return productId;
    }

    public void setProductId(Long productId) {
        this.productId = productId;
    }

    public String getProductCode() {
        return productCode;
    }

    public void setProductCode(String productCode) {
        this.productCode = productCode;
    }

    public String getFlowCardNo() {
        return flowCardNo;
    }

    public void setFlowCardNo(String flowCardNo) {
        this.flowCardNo = flowCardNo;
    }

    public Long getRoutingId() {
        return routingId;
    }

    public void setRoutingId(Long routingId) {
        this.routingId = routingId;
    }

    public LocalDateTime getIssueTime() {
        return issueTime;
    }

    public void setIssueTime(LocalDateTime issueTime) {
        this.issueTime = issueTime;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
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

    public String getSourceExecutionMode() {
        return sourceExecutionMode;
    }

    public void setSourceExecutionMode(String sourceExecutionMode) {
        this.sourceExecutionMode = sourceExecutionMode;
    }

    public String getExternalEffect() {
        return externalEffect;
    }

    public void setExternalEffect(String externalEffect) {
        this.externalEffect = externalEffect;
    }

    public String getExternalBindingKey() {
        return externalBindingKey;
    }

    public void setExternalBindingKey(String externalBindingKey) {
        this.externalBindingKey = externalBindingKey;
    }

    public String getExternalReference() {
        return externalReference;
    }

    public void setExternalReference(String externalReference) {
        this.externalReference = externalReference;
    }

    public String getVoidOperationId() {
        return voidOperationId;
    }

    public void setVoidOperationId(String voidOperationId) {
        this.voidOperationId = voidOperationId;
    }

    public String getVoidFromStatus() {
        return voidFromStatus;
    }

    public void setVoidFromStatus(String voidFromStatus) {
        this.voidFromStatus = voidFromStatus;
    }

    public String getVoidResultStatus() {
        return voidResultStatus;
    }

    public void setVoidResultStatus(String voidResultStatus) {
        this.voidResultStatus = voidResultStatus;
    }

    public String getVoidExecutionMode() {
        return voidExecutionMode;
    }

    public void setVoidExecutionMode(String voidExecutionMode) {
        this.voidExecutionMode = voidExecutionMode;
    }

    public LocalDateTime getVoidRequestedAt() {
        return voidRequestedAt;
    }

    public void setVoidRequestedAt(LocalDateTime voidRequestedAt) {
        this.voidRequestedAt = voidRequestedAt;
    }

    public LocalDateTime getVoidFinishedAt() {
        return voidFinishedAt;
    }

    public void setVoidFinishedAt(LocalDateTime voidFinishedAt) {
        this.voidFinishedAt = voidFinishedAt;
    }

    public String getVoidMessage() {
        return voidMessage;
    }

    public void setVoidMessage(String voidMessage) {
        this.voidMessage = voidMessage;
    }
}