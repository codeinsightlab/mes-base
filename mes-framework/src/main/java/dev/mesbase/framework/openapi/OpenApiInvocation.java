package dev.mesbase.framework.openapi;

import dev.mesbase.common.openapi.OpenApiPrincipal;

public class OpenApiInvocation {
    private final OpenApiPrincipal principal;
    private final String apiCode;

    public OpenApiPrincipal getPrincipal() {
        return principal;
    }

    public String getApiCode() {
        return apiCode;
    }

    public OpenApiInvocation(OpenApiPrincipal principal, String apiCode) {
        this.principal = principal;
        this.apiCode = apiCode;
    }
}
