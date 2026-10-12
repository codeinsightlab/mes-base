package dev.mesbase.framework.openapi;


/**
 * 为重复申请返回同一Token，恢复材料仅存于受保护的Redis，不落库、不写日志。
 */
public class OpenApiClientTokenRecord {
    private String accessToken;
    private String tokenDigest;
    private long credentialVersion;
    private long expiresAt;

    public String getAccessToken() {
        return accessToken;
    }

    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
    }

    public String getTokenDigest() {
        return tokenDigest;
    }

    public void setTokenDigest(String tokenDigest) {
        this.tokenDigest = tokenDigest;
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
