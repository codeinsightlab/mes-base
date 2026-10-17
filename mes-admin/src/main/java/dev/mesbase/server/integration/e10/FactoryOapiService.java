package dev.mesbase.server.integration.e10;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.mesbase.core.error.ErrorCode;
import dev.mesbase.core.error.FoundationException;
import dev.mesbase.infrastructure.persistence.PlatformPersistence;
import dev.mesbase.server.managed.DatasourcePasswordCipher;
import dev.mesbase.server.managed.FactoryMetadataCache;
import dev.mesbase.system.factory.FactoryOapiConfiguration;
import dev.mesbase.system.factory.ManagedFactory;
import dev.mesbase.system.factory.ManagedFactoryMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;

/**
 * 复用中心工厂、加密和事务入口；配置准备不切换业务运行模式。
 */
public class FactoryOapiService {
    public record Form(@Min(1) long factoryRevision, @NotNull @Valid FactoryOapiSettings settings) {
        @Override
        public String toString() {
            return "FactoryOapiForm[REDACTED]";
        }
    }

    public record View(String factoryId, long factoryRevision, String reference, long configurationRevision,
                       boolean locked, FactoryOapiSettings settings) {
        @Override
        public String toString() {
            return "FactoryOapiView[REDACTED]";
        }
    }

    public record Connection(String factoryId, String reference, long revision, FactoryOapiSettings settings) {
        @Override
        public String toString() {
            return "FactoryOapiConnection[REDACTED]";
        }
    }

    private final ManagedFactoryMapper mapper;
    private final PlatformPersistence persistence;
    private final DatasourcePasswordCipher cipher;
    private final FactoryMetadataCache metadata;
    private final ObjectMapper json = new ObjectMapper();

    public FactoryOapiService(ManagedFactoryMapper mapper, PlatformPersistence persistence,
                              DatasourcePasswordCipher cipher, FactoryMetadataCache metadata) {
        this.mapper = mapper;
        this.persistence = persistence;
        this.cipher = cipher;
        this.metadata = metadata;
    }

    @Transactional(transactionManager = "masterTransactionManager", readOnly = true)
    public View get(String factoryId) {
        var factory = required(mapper.find(factoryId));
        var row = mapper.oapi(factoryId);
        var connection = decode(row);
        return new View(factoryId, factory.revision(), row.reference(), row.configurationRevision(),
                locked(factory), connection == null ? null : connection.settings());
    }

    @Transactional(transactionManager = "masterTransactionManager")
    public View save(String factoryId, Form form) {
        if (form.settings() == null) throw new FoundationException(ErrorCode.BAD_REQUEST);
        try {
            form.settings().validate();
        } catch (IllegalArgumentException invalid) {
            throw new FoundationException(ErrorCode.BAD_REQUEST);
        }
        var factory = required(mapper.find(factoryId));
        if (factory.revision() != form.factoryRevision()) throw new FoundationException(ErrorCode.RESOURCE_CONFLICT);
        var old = mapper.oapi(factoryId);
        var current = decode(old);
        if (locked(factory)) throw new FoundationException(ErrorCode.FACTORY_MODE_CHANGE_REQUIRES_MIGRATION);
        if (current != null && current.settings().equals(form.settings())) return get(factoryId);
        String reference = old.reference() == null ? "e10-" + UUID.randomUUID() : old.reference();
        String encrypted;
        try {
            encrypted = cipher.encrypt(json.writeValueAsString(form.settings()));
        } catch (JsonProcessingException invalid) {
            throw new FoundationException(ErrorCode.INTEGRATION_NOT_CONFIGURED, invalid);
        }
        var next = new FactoryOapiConfiguration(factoryId, reference, encrypted,
                old.configurationRevision() + 1, factory.revision());
        if (mapper.updateOapi(next) != 1) throw new FoundationException(ErrorCode.RESOURCE_CONFLICT);
        metadata.changed(List.of(factoryId));
        return get(factoryId);
    }

    /**
     * 普通查询使用注入 Mapper；仅业务事务内准入使用既有独立只读窗口。
     */
    public Connection connection(String factoryId) {
        FactoryOapiConfiguration row = persistence.transactionManager().foreignTransactionActive()
                ? persistence.readOnly(session -> session.mapper(ManagedFactoryMapper.class).oapi(factoryId))
                : mapper.oapi(factoryId);
        return decode(row);
    }

    private Connection decode(FactoryOapiConfiguration row) {
        if (row == null || row.settingsCipher() == null && row.reference() == null && row.configurationRevision() == 0)
            return null;
        if (row.reference() == null || row.settingsCipher() == null || row.configurationRevision() < 1)
            throw new FoundationException(ErrorCode.INTEGRATION_NOT_CONFIGURED);
        try {
            var settings = json.readValue(cipher.decrypt(row.settingsCipher()), FactoryOapiSettings.class);
            settings.validate();
            return new Connection(row.factoryId(), row.reference(), row.configurationRevision(), settings);
        } catch (JsonProcessingException | IllegalArgumentException | IllegalStateException invalid) {
            throw new FoundationException(ErrorCode.INTEGRATION_NOT_CONFIGURED);
        }
    }

    private static boolean locked(ManagedFactory factory) {
        return "ERP".equals(factory.runtimeMode()) || factory.erpConfigRef() != null;
    }

    private static ManagedFactory required(ManagedFactory factory) {
        if (factory == null) throw new FoundationException(ErrorCode.NOT_FOUND);
        return factory;
    }
}
