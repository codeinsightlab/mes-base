package dev.mesbase.system.factory;

/**
 * SOURCE_PRODUCT_PORT: Factory identity and business datasource binding.
 */
public record ManagedFactory(String factoryId, String enterpriseId, String factoryCode, String factoryName,
                             Long datasourceId, String status, long revision) {
}
