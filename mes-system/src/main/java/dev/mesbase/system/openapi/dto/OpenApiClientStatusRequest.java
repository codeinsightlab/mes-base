package dev.mesbase.system.openapi.dto;

import jakarta.validation.constraints.*;

public class OpenApiClientStatusRequest {
    @NotNull private Long clientId;
    @NotNull private Integer status;

    public Long getClientId() { return clientId; }
    public void setClientId(Long clientId) { this.clientId = clientId; }
    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
}
