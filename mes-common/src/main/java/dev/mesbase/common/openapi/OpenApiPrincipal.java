package dev.mesbase.common.openapi;

import java.security.Principal;

/**
 * 已验证的外部身份；不属于内部 LoginUser。
 */
public final class OpenApiPrincipal implements Principal {
    private final Long clientId;
    private final String appKey;
    private final long credentialVersion;

    public OpenApiPrincipal(Long clientId, String appKey, long credentialVersion) {
        this.clientId = clientId;
        this.appKey = appKey;
        this.credentialVersion = credentialVersion;
    }

    public Long getClientId() {
        return clientId;
    }

    public String getAppKey() {
        return appKey;
    }

    public long getCredentialVersion() {
        return credentialVersion;
    }

    @Override
    public String getName() {
        return appKey;
    }
}
