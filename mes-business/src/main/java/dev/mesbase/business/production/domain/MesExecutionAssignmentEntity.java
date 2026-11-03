package dev.mesbase.business.production.domain;

import java.time.LocalDateTime;

public class MesExecutionAssignmentEntity {
    private Long id;

    private String executionCode;

    private Long routingNodeId;

    private Long userId;

    private Long assignedBy;

    private LocalDateTime assignedAt;

    private String remark;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getExecutionCode() {
        return executionCode;
    }

    public void setExecutionCode(String executionCode) {
        this.executionCode = executionCode;
    }

    public Long getRoutingNodeId() {
        return routingNodeId;
    }

    public void setRoutingNodeId(Long routingNodeId) {
        this.routingNodeId = routingNodeId;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public Long getAssignedBy() {
        return assignedBy;
    }

    public void setAssignedBy(Long assignedBy) {
        this.assignedBy = assignedBy;
    }

    public LocalDateTime getAssignedAt() {
        return assignedAt;
    }

    public void setAssignedAt(LocalDateTime assignedAt) {
        this.assignedAt = assignedAt;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }
}