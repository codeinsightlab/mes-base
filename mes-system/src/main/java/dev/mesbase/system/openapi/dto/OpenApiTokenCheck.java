package dev.mesbase.system.openapi.dto;


public class OpenApiTokenCheck {
    private final Long clientId;
    private final long credentialVersion;

    public Long getClientId() { return clientId; }
    public long getCredentialVersion() { return credentialVersion; }
    public OpenApiTokenCheck(Long clientId, long credentialVersion) { this.clientId = clientId; this.credentialVersion = credentialVersion; }
}
