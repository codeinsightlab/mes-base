package dev.mesbase.framework.openapi;


/**
 * Token locator and verifier are stored as value fields; neither is used as a credential by itself.
 */
public class OpenApiTokenRecord {
    private Long clientId;
    private long credentialVersion;
    private long expiresAt;
    private String tokenId;
    private String tokenDigest;

    public String getTokenId() { return tokenId; }
    public void setTokenId(String tokenId) { this.tokenId = tokenId; }
    public String getTokenDigest() { return tokenDigest; }
    public void setTokenDigest(String tokenDigest) { this.tokenDigest = tokenDigest; }

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
