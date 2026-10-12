package dev.mesbase.framework.openapi;


/**
 * Redis仅存随机Token的摘要作为key，value不含Token或secret。
 */
public class OpenApiTokenRecord {
    private Long clientId;
    private long credentialVersion;
    private long expiresAt;

    public Long getClientId() {
        return clientId;
    }

    public void setClientId(Long clientId) {
        this.clientId = clientId;
    }

    public long getCredentialVersion() {
        return credentialVersion;
    }

    public void setCredentialVersion(long credentialVersion) {
        this.credentialVersion = credentialVersion;
    }

    public long getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(long expiresAt) {
        this.expiresAt = expiresAt;
    }
}
