// MES-CODEGEN: MyBatis Generator standard single-table persistence.
package dev.mesbase.business.production.domain;

import java.time.LocalDateTime;

public class MesWorkorderCancellationEntity {
    private String id;

    private Long workorderId;

    private String requestFingerprint;

    private String reason;

    private Long operatorUserId;

    private LocalDateTime recordedAt;

    private String previousStatus;

    private String sourceSnapshot;

    private Integer previousRevision;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public Long getWorkorderId() {
        return workorderId;
    }

    public void setWorkorderId(Long workorderId) {
        this.workorderId = workorderId;
    }

    public String getRequestFingerprint() {
        return requestFingerprint;
    }

    public void setRequestFingerprint(String requestFingerprint) {
        this.requestFingerprint = requestFingerprint;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
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

    public String getPreviousStatus() {
        return previousStatus;
    }

    public void setPreviousStatus(String previousStatus) {
        this.previousStatus = previousStatus;
    }

    public String getSourceSnapshot() {
        return sourceSnapshot;
    }

    public void setSourceSnapshot(String sourceSnapshot) {
        this.sourceSnapshot = sourceSnapshot;
    }

    public Integer getPreviousRevision() {
        return previousRevision;
    }

    public void setPreviousRevision(Integer previousRevision) {
        this.previousRevision = previousRevision;
    }
}