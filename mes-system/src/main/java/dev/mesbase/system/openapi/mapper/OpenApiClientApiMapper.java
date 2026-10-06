package dev.mesbase.system.openapi.mapper;

import dev.mesbase.system.openapi.domain.OpenApiClientApi;
import java.util.List;

/** 基础CRUD，结构来源为 sql/openapi/20260930-open-api-platform.sql。 */
@dev.mesbase.infrastructure.persistence.PersistenceOwner(dev.mesbase.infrastructure.persistence.PersistenceOwner.Kind.PLATFORM)
public interface OpenApiClientApiMapper {
    OpenApiClientApi selectById(Long id);
    List<OpenApiClientApi> selectList(OpenApiClientApi filter);
    int insert(OpenApiClientApi entity);
    int update(OpenApiClientApi entity);
    int deleteById(Long id);
    int deleteByIds(Long[] ids);
}
