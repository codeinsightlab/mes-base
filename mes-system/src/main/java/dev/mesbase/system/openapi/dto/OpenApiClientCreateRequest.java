package dev.mesbase.system.openapi.dto;

import jakarta.validation.constraints.*;

public class OpenApiClientCreateRequest {
    @NotBlank @Size(max = 100) private String clientName;
    @Size(max = 500) private String remark;

    public String getClientName() { return clientName; }
    public void setClientName(String clientName) { this.clientName = clientName; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
}
