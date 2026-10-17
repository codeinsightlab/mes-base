package dev.mesbase.system.factory;

import org.apache.ibatis.annotations.Select;

/**
 * Joined routing definitions for explicit and automatic factory selection.
 */
public interface RoutingMetadataMapper {
    String COLUMNS = "f.factory_id,f.enterprise_id,f.factory_code,f.factory_name,f.datasource_id,f.status AS factory_status,"
            + "f.revision AS factory_revision,d.datasource_name,d.db_host,d.db_port,d.db_name,d.db_username,d.password_cipher,"
            + "d.status AS datasource_status,d.revision AS datasource_revision,f.runtime_mode,f.erp_provider,f.erp_config_ref,f.integration_revision,f.integration_locked";

    @Select("SELECT " + COLUMNS + " FROM mf_factory f LEFT JOIN mf_datasource d ON f.datasource_id=d.datasource_id WHERE f.factory_id=#{id}")
    FactoryExecutionMetadata definition(String id);

    @Select("SELECT " + COLUMNS + " FROM mf_factory f JOIN mf_datasource d ON f.datasource_id=d.datasource_id "
            + "WHERE f.status='ENABLED' AND d.status='ENABLED' "
            + "ORDER BY CASE WHEN f.factory_id REGEXP '^[0-9]+$' THEN 0 ELSE 1 END,"
            + "CASE WHEN f.factory_id REGEXP '^[0-9]+$' THEN CAST(f.factory_id AS DECIMAL(65,0)) END,"
            + "f.factory_id LIMIT 1")
    FactoryExecutionMetadata defaultDefinition();
}
