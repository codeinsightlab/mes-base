package dev.mesbase.business.production.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class MesProductionReportEntity {
    private Long id;

    private Long executionId;

    private Long routingNodeId;

    private String recordType;

    private BigDecimal quantityDelta;

    private Long referenceReportId;

    private Long operatorUserId;

    private Long recordedBy;

    private LocalDateTime reportTime;

    private Long workstationId;

    private String reason;

    private LocalDateTime createTime;

    private String requestId;

    private String commandFingerprint;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
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

    public Long getReferenceReportId() {
        return referenceReportId;
    }

    public void setReferenceReportId(Long referenceReportId) {
        this.referenceReportId = referenceReportId;
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

    public LocalDateTime getReportTime() {
        return reportTime;
    }

    public void setReportTime(LocalDateTime reportTime) {
        this.reportTime = reportTime;
    }

    public Long getWorkstationId() {
        return workstationId;
    }

    public void setWorkstationId(Long workstationId) {
        this.workstationId = workstationId;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public String getCommandFingerprint() {
        return commandFingerprint;
    }

    public void setCommandFingerprint(String commandFingerprint) {
        this.commandFingerprint = commandFingerprint;
    }
}
