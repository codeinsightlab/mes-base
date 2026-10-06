package dev.mesbase.system.openapi.dto;

import dev.mesbase.common.openapi.OpenApiPrincipal;

public class OpenApiAuthorizationQuery {
    private final OpenApiPrincipal principal;
    private final String apiCode;

    public OpenApiPrincipal getPrincipal() { return principal; }
    public String getApiCode() { return apiCode; }
    public OpenApiAuthorizationQuery(OpenApiPrincipal principal, String apiCode) { this.principal = principal; this.apiCode = apiCode; }
}
