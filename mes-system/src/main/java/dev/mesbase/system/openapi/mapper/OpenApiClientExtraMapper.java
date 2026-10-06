package dev.mesbase.system.openapi.mapper;

import dev.mesbase.system.openapi.domain.OpenApiClient;
import dev.mesbase.system.openapi.domain.OpenApiCredentialChange;

public interface OpenApiClientExtraMapper {
    OpenApiClient selectByAppKey(String appKey);
    OpenApiClient lockById(Long clientId);
    int changeCredentials(OpenApiCredentialChange change);
}
