package dev.mesbase.system.factory;

/**
 * 中心工厂记录上的加密连接配置，禁止直接作为 HTTP 响应。
 */
public record FactoryOapiConfiguration(String factoryId, String reference, String settingsCipher,
                                       long configurationRevision, long factoryRevision) {
    @Override
    public String toString() {
        return "FactoryOapiConfiguration[REDACTED]";
    }
}
