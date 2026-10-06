package dev.mesbase.system.openapi.mapper;

import dev.mesbase.system.openapi.domain.OpenApiClient;
import java.util.List;

/** 基础CRUD，结构来源为 sql/openapi/20260930-open-api-platform.sql。 */
public interface OpenApiClientMapper {
    OpenApiClient selectById(Long id);
    List<OpenApiClient> selectList(OpenApiClient filter);
    int insert(OpenApiClient entity);
    int update(OpenApiClient entity);
    int deleteById(Long id);
    int deleteByIds(Long[] ids);
}
