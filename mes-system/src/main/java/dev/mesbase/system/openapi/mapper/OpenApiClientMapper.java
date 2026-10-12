package dev.mesbase.system.openapi.mapper;

import dev.mesbase.system.openapi.domain.OpenApiClient;
import dev.mesbase.system.openapi.domain.OpenApiCredentialChange;
import java.util.List;

/**
 * 基础CRUD，结构来源为 sql/openapi/20260930-open-api-platform.sql。
 */
public interface OpenApiClientMapper {
    OpenApiClient selectById(Long id);

    OpenApiClient selectByAppKey(String appKey);

    OpenApiClient lockById(Long clientId);

    int changeCredentials(OpenApiCredentialChange change);

    List<OpenApiClient> selectList(OpenApiClient filter);

    int insert(OpenApiClient entity);

    int update(OpenApiClient entity);

    int deleteById(Long id);

    int deleteByIds(Long[] ids);
}
