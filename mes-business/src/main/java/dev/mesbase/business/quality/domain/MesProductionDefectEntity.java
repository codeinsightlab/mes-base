// MES-CODEGEN: MyBatis Generator standard single-table persistence.
package dev.mesbase.business.quality.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class MesProductionDefectEntity {
    private Long id;

    private String declarationId;

    private Integer revisionNo;

    private String recordAction;

    private String revisionReason;

    private Long executionId;

    private Long affectedNodeId;

    private Long discoveredNodeId;

    private Long responsibleNodeId;

    private BigDecimal defectQty;

    private String wasteCategory;

    private String defectReason;

    private Long responsibleUserId;

    private String responsibilityRemark;

    private String destinationType;

    private Long targetNodeId;

    private Long targetWarehouseId;

    private String destinationRemark;

    private String requestKey;

    private String requestFingerprint;

    private Long reportedBy;

    private LocalDateTime occurredAt;

    private Long recordedBy;

    private LocalDateTime recordedAt;

    private Long defectTypeId;

    private String defectTypeCodeSnapshot;

    private String defectTypeNameSnapshot;

    private Integer defectTypeRevision;

    private String costCategory;

    private String commandVersion;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getDeclarationId() {
        return declarationId;
    }

    public void setDeclarationId(String declarationId) {
        this.declarationId = declarationId;
    }

    public Integer getRevisionNo() {
        return revisionNo;
    }

    public void setRevisionNo(Integer revisionNo) {
        this.revisionNo = revisionNo;
    }

    public String getRecordAction() {
        return recordAction;
    }

    public void setRecordAction(String recordAction) {
        this.recordAction = recordAction;
    }

    public String getRevisionReason() {
        return revisionReason;
    }

    public void setRevisionReason(String revisionReason) {
        this.revisionReason = revisionReason;
    }

    public Long getExecutionId() {
        return executionId;
    }

    public void setExecutionId(Long executionId) {
        this.executionId = executionId;
    }

    public Long getAffectedNodeId() {
        return affectedNodeId;
    }

    public void setAffectedNodeId(Long affectedNodeId) {
        this.affectedNodeId = affectedNodeId;
    }

    public Long getDiscoveredNodeId() {
        return discoveredNodeId;
    }

    public void setDiscoveredNodeId(Long discoveredNodeId) {
        this.discoveredNodeId = discoveredNodeId;
    }

    public Long getResponsibleNodeId() {
        return responsibleNodeId;
    }

    public void setResponsibleNodeId(Long responsibleNodeId) {
        this.responsibleNodeId = responsibleNodeId;
    }

    public BigDecimal getDefectQty() {
        return defectQty;
    }

    public void setDefectQty(BigDecimal defectQty) {
        this.defectQty = defectQty;
    }

    public String getWasteCategory() {
        return wasteCategory;
    }

    public void setWasteCategory(String wasteCategory) {
        this.wasteCategory = wasteCategory;
    }

    public String getDefectReason() {
        return defectReason;
    }

    public void setDefectReason(String defectReason) {
        this.defectReason = defectReason;
    }

    public Long getResponsibleUserId() {
        return responsibleUserId;
    }

    public void setResponsibleUserId(Long responsibleUserId) {
        this.responsibleUserId = responsibleUserId;
    }

    public String getResponsibilityRemark() {
        return responsibilityRemark;
    }

    public void setResponsibilityRemark(String responsibilityRemark) {
        this.responsibilityRemark = responsibilityRemark;
    }

    public String getDestinationType() {
        return destinationType;
    }

    public void setDestinationType(String destinationType) {
        this.destinationType = destinationType;
    }

    public Long getTargetNodeId() {
        return targetNodeId;
    }

    public void setTargetNodeId(Long targetNodeId) {
        this.targetNodeId = targetNodeId;
    }

    public Long getTargetWarehouseId() {
        return targetWarehouseId;
    }

    public void setTargetWarehouseId(Long targetWarehouseId) {
        this.targetWarehouseId = targetWarehouseId;
    }

    public String getDestinationRemark() {
        return destinationRemark;
    }

    public void setDestinationRemark(String destinationRemark) {
        this.destinationRemark = destinationRemark;
    }

    public String getRequestKey() {
        return requestKey;
    }

    public void setRequestKey(String requestKey) {
        this.requestKey = requestKey;
    }

    public String getRequestFingerprint() {
        return requestFingerprint;
    }

    public void setRequestFingerprint(String requestFingerprint) {
        this.requestFingerprint = requestFingerprint;
    }

    public Long getReportedBy() {
        return reportedBy;
    }

    public void setReportedBy(Long reportedBy) {
        this.reportedBy = reportedBy;
    }

    public LocalDateTime getOccurredAt() {
        return occurredAt;
    }

    public void setOccurredAt(LocalDateTime occurredAt) {
        this.occurredAt = occurredAt;
    }

    public Long getRecordedBy() {
        return recordedBy;
    }

    public void setRecordedBy(Long recordedBy) {
        this.recordedBy = recordedBy;
    }

    public LocalDateTime getRecordedAt() {
        return recordedAt;
    }

    public void setRecordedAt(LocalDateTime recordedAt) {
        this.recordedAt = recordedAt;
    }

    public Long getDefectTypeId() {
        return defectTypeId;
    }

    public void setDefectTypeId(Long defectTypeId) {
        this.defectTypeId = defectTypeId;
    }

    public String getDefectTypeCodeSnapshot() {
        return defectTypeCodeSnapshot;
    }

    public void setDefectTypeCodeSnapshot(String defectTypeCodeSnapshot) {
        this.defectTypeCodeSnapshot = defectTypeCodeSnapshot;
    }

    public String getDefectTypeNameSnapshot() {
        return defectTypeNameSnapshot;
    }

    public void setDefectTypeNameSnapshot(String defectTypeNameSnapshot) {
        this.defectTypeNameSnapshot = defectTypeNameSnapshot;
    }

    public Integer getDefectTypeRevision() {
        return defectTypeRevision;
    }

    public void setDefectTypeRevision(Integer defectTypeRevision) {
        this.defectTypeRevision = defectTypeRevision;
    }

    public String getCostCategory() {
        return costCategory;
    }

    public void setCostCategory(String costCategory) {
        this.costCategory = costCategory;
    }

    public String getCommandVersion() {
        return commandVersion;
    }

    public void setCommandVersion(String commandVersion) {
        this.commandVersion = commandVersion;
    }
}