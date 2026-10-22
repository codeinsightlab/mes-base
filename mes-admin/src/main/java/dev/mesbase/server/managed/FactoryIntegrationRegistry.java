package dev.mesbase.server.managed;

import dev.mesbase.common.utils.StringUtils;
import dev.mesbase.core.error.ErrorCode;
import dev.mesbase.core.error.FoundationException;
import dev.mesbase.core.factory.FactoryId;
import dev.mesbase.core.factory.FactoryRuntimeMode;
import dev.mesbase.platform.execution.FactoryIntegrationSnapshot;
import dev.mesbase.server.integration.e10.DigiwinE10MoQueries;
import dev.mesbase.server.integration.e10.FactoryOapiService;
import dev.mesbase.system.factory.ManagedFactory;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
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
    private final FactoryOapiService oapi;

    public FactoryIntegrationRegistry(Environment environment) {
        this(environment, null);
    }

    public FactoryIntegrationRegistry(Environment environment, FactoryOapiService oapi) {
        this.environment = environment;
        this.oapi = oapi;
        connections = Map.copyOf(Binder.get(environment).bind("mes.integration.connections",
                Bindable.mapOf(String.class, Connection.class)).orElse(Map.of()));
    }

    public List<Choice> choices(String factoryId) {
        var choices = new ArrayList<Choice>();
        if (oapi != null) {
            var configured = oapi.connection(factoryId);
            if (configured != null)
                choices.add(new Choice(configured.reference(), DigiwinE10MoQueries.PROVIDER, configured.revision()));
        }
        connections.entrySet().stream().filter(e -> Objects.equals(factoryId, e.getValue().factoryId()))
                .filter(e -> !DigiwinE10MoQueries.PROVIDER.equals(e.getValue().provider()))
                .filter(e -> valid(e.getValue()))
                .map(e -> new Choice(e.getKey(), e.getValue().provider(), e.getValue().revision()))
                .forEach(choices::add);
        return choices.stream().sorted((a, b) -> a.reference().compareTo(b.reference())).toList();
    }

    private boolean valid(Connection connection) {
        if (!connection.enabled() || connection.revision() < 1 || connection.provider() == null
                || !connection.provider()
                .matches("[A-Za-z0-9_-]{1,128}") || "local".equalsIgnoreCase(connection.provider())
                || StringUtils.isBlank(connection.organization())
                || StringUtils.isBlank(connection.credentialReference())
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
        return StringUtils.isNotBlank(credential);
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
        if (StringUtils.isBlank(factory.erpProvider())
                || StringUtils.isBlank(factory.erpConfigRef()))
            throw new FoundationException(ErrorCode.INTEGRATION_NOT_CONFIGURED);
        if (DigiwinE10MoQueries.PROVIDER.equals(factory.erpProvider())) {
            var configured = Optional.ofNullable(oapi)
                    .map(nullableValue -> nullableValue.connection(factory.factoryId())).orElse(null);
            if (configured == null || !Objects.equals(factory.erpConfigRef(), configured.reference()))
                throw new FoundationException(ErrorCode.INTEGRATION_NOT_CONFIGURED);
            return new FactoryIntegrationSnapshot(new FactoryId(factory.factoryId()), mode, factory.erpProvider(),
                    factory.erpConfigRef(), factory.integrationRevision(), configured.revision());
        }
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
