package dev.mesbase.system.factory;

/**
 * Immutable definition; encrypted password only. Not a runtime connection or an authorization.
 */
public record FactoryExecutionMetadata(String factoryId, String enterpriseId, String factoryCode, String factoryName,
                                       Long datasourceId, String factoryStatus, long factoryRevision,
                                       String datasourceName, String dbHost,
                                       Integer dbPort, String dbName, String dbUsername, String passwordCipher,
                                       String datasourceStatus, Long datasourceRevision) {
    public ManagedFactory factory() {
        return new ManagedFactory(factoryId, enterpriseId, factoryCode, factoryName, datasourceId, factoryStatus, factoryRevision);
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
