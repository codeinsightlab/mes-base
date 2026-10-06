package dev.mesbase.system.openapi.dto;

import jakarta.validation.constraints.*;

public class OpenApiClientIdRequest {
    @NotNull private Long clientId;

    public Long getClientId() { return clientId; }
    public void setClientId(Long clientId) { this.clientId = clientId; }
}
