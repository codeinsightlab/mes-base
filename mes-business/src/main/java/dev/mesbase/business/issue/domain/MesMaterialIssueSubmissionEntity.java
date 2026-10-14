// MES-CODEGEN: MyBatis Generator standard single-table persistence.
package dev.mesbase.business.issue.domain;

import java.time.LocalDateTime;

public class MesMaterialIssueSubmissionEntity {
    private Long id;

    private Long issueId;

    private Integer pathRevision;

    private String status;

    private LocalDateTime createdAt;

    private LocalDateTime finishedAt;

    private String commandJson;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getIssueId() {
        return issueId;
    }

    public void setIssueId(Long issueId) {
        this.issueId = issueId;
    }

    public Integer getPathRevision() {
        return pathRevision;
    }

    public void setPathRevision(Integer pathRevision) {
        this.pathRevision = pathRevision;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getFinishedAt() {
        return finishedAt;
    }

    public void setFinishedAt(LocalDateTime finishedAt) {
        this.finishedAt = finishedAt;
    }

    public String getCommandJson() {
        return commandJson;
    }

    public void setCommandJson(String commandJson) {
        this.commandJson = commandJson;
    }
}