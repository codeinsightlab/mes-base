package dev.mesbase.server.managed;

import dev.mesbase.core.error.ErrorCode;
import dev.mesbase.core.error.FoundationException;
import dev.mesbase.core.factory.FactoryId;
import dev.mesbase.core.factory.FactoryRuntimeMode;
import dev.mesbase.platform.execution.FactoryIntegrationSnapshot;
import dev.mesbase.system.factory.ManagedFactory;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.env.Environment;

/**
 * Uses server-owned opaque binding keys and provider identifiers, without a second provider enum or secret store.
 */
public final class FactoryIntegrationRegistry {
    public record Connection(String factoryId, String provider, String serviceUrl, String credentialReference,
                             String organization, boolean enabled, long revision) {
        @Override
        public String toString() {
            return "Connection[REDACTED]";
        }
    }

    public record Choice(String reference, String provider, long revision) {
    }

    private final Map<String, Connection> connections;
    private final Environment environment;

    public FactoryIntegrationRegistry(Environment environment) {
        this.environment = environment;
        connections = Map.copyOf(Binder.get(environment).bind("mes.integration.connections",
                Bindable.mapOf(String.class, Connection.class)).orElse(Map.of()));
    }

    public List<Choice> choices(String factoryId) {
        return connections.entrySet().stream().filter(e -> Objects.equals(factoryId, e.getValue().factoryId()))
                .filter(e -> valid(e.getValue()))
                .map(e -> new Choice(e.getKey(), e.getValue().provider(), e.getValue().revision()))
                .sorted((a, b) -> a.reference().compareTo(b.reference())).toList();
    }

    /**
     * 服务端消费方装配复用现有连接准入，不向 HTTP 响应暴露连接定义。
     */
    public Connection requireConnection(String reference) {
        var connection = reference == null ? null : connections.get(reference);
        if (connection == null || connection.factoryId() == null || connection.factoryId()
                .isBlank() || !valid(connection))
            throw new FoundationException(ErrorCode.INTEGRATION_NOT_CONFIGURED);
        return connection;
    }

    private boolean valid(Connection connection) {
        if (!connection.enabled() || connection.revision() < 1 || connection.provider() == null
                || !connection.provider()
                .matches("[A-Za-z0-9_-]{1,128}") || "local".equalsIgnoreCase(connection.provider())
                || connection.organization() == null || connection.organization().isBlank()
                || connection.credentialReference() == null || connection.credentialReference().isBlank()
                || connection.serviceUrl() == null) return false;
        try {
            var uri = URI.create(connection.serviceUrl());
            if (!("https".equals(uri.getScheme()) || "http".equals(uri.getScheme())) || uri.getHost() == null
                    || uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null) return false;
        } catch (IllegalArgumentException invalid) {
            return false;
        }
        // Reference resolves only on the server. Its value is never copied into any view or snapshot.
        String credential = environment.getProperty(connection.credentialReference());
        return credential != null && !credential.isBlank();
    }

    public FactoryIntegrationSnapshot snapshot(ManagedFactory factory) {
        FactoryRuntimeMode mode;
        try {
            mode = FactoryRuntimeMode.valueOf(factory.runtimeMode());
        } catch (IllegalArgumentException | NullPointerException invalid) {
            throw new FoundationException(ErrorCode.INTEGRATION_NOT_CONFIGURED, invalid);
        }
        if (mode == FactoryRuntimeMode.LOCAL)
            return new FactoryIntegrationSnapshot(new FactoryId(factory.factoryId()), mode, factory.erpProvider(),
                    factory.erpConfigRef(), factory.integrationRevision(), null);
        if (factory.erpProvider() == null || factory.erpProvider().isBlank()
                || factory.erpConfigRef() == null || factory.erpConfigRef().isBlank())
            throw new FoundationException(ErrorCode.INTEGRATION_NOT_CONFIGURED);
        var connection = connections.get(factory.erpConfigRef());
        if (connection == null || !Objects.equals(factory.factoryId(), connection.factoryId())
                || !Objects.equals(factory.erpProvider(), connection.provider()) || !valid(connection))
            throw new FoundationException(ErrorCode.INTEGRATION_NOT_CONFIGURED);
        return new FactoryIntegrationSnapshot(new FactoryId(factory.factoryId()), mode, factory.erpProvider(),
                factory.erpConfigRef(), factory.integrationRevision(), connection.revision());
    }

    public static ManagedFactory configure(ManagedFactory old, ManagedFactory proposed) {
        boolean changed = old != null && (!Objects.equals(old.runtimeMode(), proposed.runtimeMode())
                || !Objects.equals(old.erpProvider(), proposed.erpProvider()) || !Objects.equals(old.erpConfigRef(), proposed.erpConfigRef()));
        if (changed && old.integrationLocked())
            throw new FoundationException(ErrorCode.FACTORY_MODE_CHANGE_REQUIRES_MIGRATION);
        return new ManagedFactory(proposed.factoryId(), proposed.enterpriseId(), proposed.factoryCode(), proposed.factoryName(),
                proposed.datasourceId(), proposed.status(), proposed.revision(), proposed.runtimeMode(), proposed.erpProvider(),
                proposed.erpConfigRef(), old == null ? 1 : old.integrationRevision() + (changed ? 1 : 0),
                proposed.datasourceId() != null || old != null && old.integrationLocked());
    }
}
