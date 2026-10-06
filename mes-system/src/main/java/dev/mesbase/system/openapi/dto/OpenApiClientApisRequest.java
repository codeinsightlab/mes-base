package dev.mesbase.system.openapi.dto;

import java.util.List;
import jakarta.validation.constraints.*;

public class OpenApiClientApisRequest {
    @NotNull private Long clientId;
    @NotNull @Size(max = 100) private List<@NotBlank @Pattern(regexp = "[a-z][a-z0-9.:_-]{0,99}") String> apiCodes;

    public Long getClientId() { return clientId; }
    public void setClientId(Long clientId) { this.clientId = clientId; }
    public List<String> getApiCodes() { return apiCodes; }
    public void setApiCodes(List<String> apiCodes) { this.apiCodes = apiCodes; }
}
