package dev.mesbase.server.integration.e10;

import dev.mesbase.common.utils.StringUtils;
import java.net.URI;

/**
 * 工厂管理专用连接参数；不作为普通工厂响应或日志内容。
 */
public record FactoryOapiSettings(String endpoint, Host host, Service service, E10SyncContract syncContract) {
    public FactoryOapiSettings(String endpoint, Host host, Service service) {
        this(endpoint, host, service, null);
    }

    public record Host(String ver, String prod, String timezone, String ip, String id, String lang, String acct) {
        @Override
        public String toString() {
            return "Host[REDACTED]";
        }
    }

    public record Service(String prod, String ip, String id) {
        @Override
        public String toString() {
            return "Service[REDACTED]";
        }
    }

    public void validate() {
        if (syncContract != null) syncContract.validate();
        required(endpoint);
        URI uri;
        try {
            uri = URI.create(endpoint);
        } catch (IllegalArgumentException invalid) {
            throw new IllegalArgumentException("OAPI 地址格式不正确");
        }
        if (!("https".equals(uri.getScheme()) || "http".equals(uri.getScheme())) || uri.getHost() == null
                || uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null
                || uri.getPort() > 65535 || uri.getPort() == 0) {
            throw new IllegalArgumentException("OAPI 地址必须为不含账号、查询参数或片段的 HTTP(S) 地址");
        }
        if (host == null || service == null) throw new IllegalArgumentException("请填写完整的 OAPI host 和 service");
        for (String value : new String[]{host.ver(), host.prod(), host.timezone(), host.ip(), host.lang(), host.acct(),
                service.prod(), service.ip(), service.id()})
            required(value);
        if (host.id() == null || !host.id().isEmpty() && host.id().isBlank())
            throw new IllegalArgumentException("host.id 必须显式填写，可以为空字符串");
        if (host.id().length() > 512 || host.id().chars().anyMatch(Character::isISOControl))
            throw new IllegalArgumentException("OAPI 字段长度或字符不合法");
    }

    private static void required(String value) {
        if (StringUtils.isBlank(value) || value.length() > 2048 || value.chars()
                .anyMatch(Character::isISOControl))
            throw new IllegalArgumentException("请填写完整的 OAPI 参数，且不能包含控制字符");
    }

    @Override
    public String toString() {
        return "FactoryOapiSettings[REDACTED]";
    }
}
