// MES-CODEGEN: MyBatis Generator standard single-table persistence.
package dev.mesbase.business.issue.domain;

import java.time.LocalDateTime;

public class MesMaterialIssueErpExtraEntity {
    private Long id;

    private String sourceExecutionMode;

    private String externalEffect;

    private String externalBindingKey;

    private String externalReference;

    private String voidResultStatus;

    private String voidExecutionMode;

    private String voidMessage;

    private String provider;

    private String externalCode;

    private String documentStatus;

    private String lastOperation;

    private String lastOutcome;

    private String lastMessage;

    private Long operationVersion;

    private LocalDateTime checkedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
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

    public String getVoidMessage() {
        return voidMessage;
    }

    public void setVoidMessage(String voidMessage) {
        this.voidMessage = voidMessage;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getExternalCode() {
        return externalCode;
    }

    public void setExternalCode(String externalCode) {
        this.externalCode = externalCode;
    }

    public String getDocumentStatus() {
        return documentStatus;
    }

    public void setDocumentStatus(String documentStatus) {
        this.documentStatus = documentStatus;
    }

    public String getLastOperation() {
        return lastOperation;
    }

    public void setLastOperation(String lastOperation) {
        this.lastOperation = lastOperation;
    }

    public String getLastOutcome() {
        return lastOutcome;
    }

    public void setLastOutcome(String lastOutcome) {
        this.lastOutcome = lastOutcome;
    }

    public String getLastMessage() {
        return lastMessage;
    }

    public void setLastMessage(String lastMessage) {
        this.lastMessage = lastMessage;
    }

    public Long getOperationVersion() {
        return operationVersion;
    }

    public void setOperationVersion(Long operationVersion) {
        this.operationVersion = operationVersion;
    }

    public LocalDateTime getCheckedAt() {
        return checkedAt;
    }

    public void setCheckedAt(LocalDateTime checkedAt) {
        this.checkedAt = checkedAt;
    }
}