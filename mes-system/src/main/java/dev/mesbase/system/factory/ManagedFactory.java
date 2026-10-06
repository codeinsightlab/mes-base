package dev.mesbase.system.factory;
/** SOURCE_PRODUCT_PORT: MfFactory; external ERP plant code intentionally excluded. */
public record ManagedFactory(String factoryId, String enterpriseId, String factoryCode, String factoryName,
                             Long datasourceId, String status, long revision) {}
