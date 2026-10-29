// MES-CODEGEN: MyBatis Generator standard single-table persistence.
package dev.mesbase.business.issue.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class MesMaterialIssueInputEntity {
    private String id;

    private String requestHash;

    private Long documentId;

    private Long executionId;

    private String inputKind;

    private BigDecimal outputQty;

    private String sourceMode;

    private Long stockId;

    private LocalDateTime confirmedAt;

    private Boolean stockApplied;

    private LocalDateTime createdAt;

    private LocalDateTime businessOccurredAt;

    private String calculationJson;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getRequestHash() {
        return requestHash;
    }

    public void setRequestHash(String requestHash) {
        this.requestHash = requestHash;
    }

    public Long getDocumentId() {
        return documentId;
    }

    public void setDocumentId(Long documentId) {
        this.documentId = documentId;
    }

    public Long getExecutionId() {
        return executionId;
    }

    public void setExecutionId(Long executionId) {
        this.executionId = executionId;
    }

    public String getInputKind() {
        return inputKind;
    }

    public void setInputKind(String inputKind) {
        this.inputKind = inputKind;
    }

    public BigDecimal getOutputQty() {
        return outputQty;
    }

    public void setOutputQty(BigDecimal outputQty) {
        this.outputQty = outputQty;
    }

    public String getSourceMode() {
        return sourceMode;
    }

    public void setSourceMode(String sourceMode) {
        this.sourceMode = sourceMode;
    }

    public Long getStockId() {
        return stockId;
    }

    public void setStockId(Long stockId) {
        this.stockId = stockId;
    }

    public LocalDateTime getConfirmedAt() {
        return confirmedAt;
    }

    public void setConfirmedAt(LocalDateTime confirmedAt) {
        this.confirmedAt = confirmedAt;
    }

    public Boolean getStockApplied() {
        return stockApplied;
    }

    public void setStockApplied(Boolean stockApplied) {
        this.stockApplied = stockApplied;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getBusinessOccurredAt() {
        return businessOccurredAt;
    }

    public void setBusinessOccurredAt(LocalDateTime businessOccurredAt) {
        this.businessOccurredAt = businessOccurredAt;
    }

    public String getCalculationJson() {
        return calculationJson;
    }

    public void setCalculationJson(String calculationJson) {
        this.calculationJson = calculationJson;
    }
}