package dev.mesbase.system.openapi.domain;

import dev.mesbase.common.core.domain.BaseEntity;

/**
 * 对应新增迁移脚本；禁止作为Controller响应。
 */
public class OpenApiClient extends BaseEntity {
    private Long clientId;
    private String appKey;
    private String secretHash;
    private String clientName;
    private Integer status;
    private Long credentialVersion;

    public Long getClientId() {
        return clientId;
    }

    public void setClientId(Long clientId) {
        this.clientId = clientId;
    }

    public String getAppKey() {
        return appKey;
    }

    public void setAppKey(String appKey) {
        this.appKey = appKey;
    }

    public String getSecretHash() {
        return secretHash;
    }

    public void setSecretHash(String secretHash) {
        this.secretHash = secretHash;
    }

    public String getClientName() {
        return clientName;
    }

    public void setClientName(String clientName) {
        this.clientName = clientName;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public Long getCredentialVersion() {
        return credentialVersion;
    }

    public void setCredentialVersion(Long credentialVersion) {
        this.credentialVersion = credentialVersion;
    }
}
