package dev.mesbase.system.factory;

import org.apache.ibatis.annotations.AutomapConstructor;

/**
 * Immutable definition; encrypted password only. Not a runtime connection or an authorization.
 */
public record FactoryExecutionMetadata(String factoryId, String enterpriseId, String factoryCode, String factoryName,
                                       Long datasourceId, String factoryStatus, long factoryRevision,
                                       String datasourceName, String dbHost,
                                       Integer dbPort, String dbName, String dbUsername, String passwordCipher,
                                       String datasourceStatus, Long datasourceRevision, String runtimeMode,
                                       String erpProvider, String erpConfigRef, long integrationRevision,
                                       boolean integrationLocked) {
    @AutomapConstructor
    public FactoryExecutionMetadata {
    }

    public FactoryExecutionMetadata(String factoryId, String enterpriseId, String factoryCode, String factoryName,
                                    Long datasourceId, String factoryStatus, long factoryRevision,
                                    String datasourceName, String dbHost, Integer dbPort, String dbName,
                                    String dbUsername, String passwordCipher, String datasourceStatus,
                                    Long datasourceRevision) {
        this(factoryId, enterpriseId, factoryCode, factoryName, datasourceId, factoryStatus, factoryRevision,
                datasourceName, dbHost, dbPort, dbName, dbUsername, passwordCipher, datasourceStatus, datasourceRevision,
                "LOCAL", null, null, 1, true);
    }

    public ManagedFactory factory() {
        return new ManagedFactory(factoryId, enterpriseId, factoryCode, factoryName, datasourceId, factoryStatus, factoryRevision, runtimeMode, erpProvider, erpConfigRef, integrationRevision, integrationLocked);
    }

    public ManagedDatasource datasource() {
        if (datasourceStatus == null || datasourceRevision == null) return null;
        return new ManagedDatasource(datasourceId, datasourceName, dbHost, dbPort, dbName, dbUsername, passwordCipher, datasourceStatus, datasourceRevision);
    }

    @Override
    public String toString() {
        return "FactoryExecutionMetadata[REDACTED]";
    }
}
