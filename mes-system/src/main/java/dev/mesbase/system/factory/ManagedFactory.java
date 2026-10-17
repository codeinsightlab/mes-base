package dev.mesbase.system.factory;

import org.apache.ibatis.annotations.AutomapConstructor;

/**
 * SOURCE_PRODUCT_PORT: Factory identity and business datasource binding.
 */
public record ManagedFactory(String factoryId, String enterpriseId, String factoryCode, String factoryName,
                             Long datasourceId, String status, long revision, String runtimeMode,
                             String erpProvider, String erpConfigRef, long integrationRevision,
                             boolean integrationLocked) {
    @AutomapConstructor
    public ManagedFactory {
    }

    public ManagedFactory(String factoryId, String enterpriseId, String factoryCode, String factoryName,
                          Long datasourceId, String status, long revision) {
        this(factoryId, enterpriseId, factoryCode, factoryName, datasourceId, status, revision, "LOCAL", null, null, 1, datasourceId != null);
    }
}
