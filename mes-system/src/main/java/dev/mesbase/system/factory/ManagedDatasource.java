package dev.mesbase.system.factory;
/** Persistence only; credentials never returned through API. */
public record ManagedDatasource(Long datasourceId, String datasourceName, String dbHost, int dbPort,
        String dbName, String dbUsername, String passwordCipher, String status, long revision) {
    @Override public String toString() { return "ManagedDatasource[REDACTED]"; }
    public String jdbcUrl() { return "jdbc:mysql://"+dbHost+":"+dbPort+"/"+dbName+"?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai"; }
}
