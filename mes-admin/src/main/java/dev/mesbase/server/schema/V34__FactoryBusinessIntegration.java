package dev.mesbase.server.schema;

import dev.mesbase.common.utils.StringUtils;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import org.flywaydb.core.api.MigrationVersion;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.core.env.Environment;

/**
 * V34: imports only explicit legacy authority; never derives LOCAL from invalid ERP settings.
 */
public final class V34__FactoryBusinessIntegration extends BaseJavaMigration {
    private final Environment environment;

    public V34__FactoryBusinessIntegration(Environment environment) {
        this.environment = environment;
    }

    @Override
    public MigrationVersion getVersion() {
        return MigrationVersion.fromVersion("34");
    }

    @Override
    public String getDescription() {
        return "factory business integration";
    }

    @Override
    public Integer getChecksum() {
        return 34101701;
    }

    @Override
    public boolean canExecuteInTransaction() {
        return false;
    }

    public record Legacy(String factoryId, String mode, String provider, String reference) {
    }

    public List<Legacy> legacy(List<String> ids) {
        var rows = new ArrayList<Legacy>();
        for (String id : ids) {
            String key = "mes.integration.warehouse.factories." + id;
            String mode = environment.getProperty(key + ".mode");
            if (mode == null || "LOCAL_ONLY".equals(mode)) {
                rows.add(new Legacy(id, "LOCAL", null, null));
            } else if ("CONNECTED".equals(mode)) {
                String provider = environment.getProperty(key + ".provider");
                String reference = environment.getProperty(key + ".binding-key");
                if (StringUtils.isBlank(provider) || StringUtils.isBlank(reference))
                    throw new IllegalStateException("Legacy ERP configuration incomplete; migration refused for " + id);
                rows.add(new Legacy(id, "ERP", provider, reference));
            } else throw new IllegalStateException("Legacy factory mode invalid; migration refused for " + id);
        }
        return List.copyOf(rows);
    }

    @Override
    public void migrate(Context context) throws SQLException {
        var connection = context.getConnection();
        var ids = new ArrayList<String>();
        try (var query = connection.prepareStatement("SELECT factory_id FROM mf_factory ORDER BY factory_id"); var rs = query.executeQuery()) {
            while (rs.next()) ids.add(rs.getString(1));
        }
        // Validate all legacy definitions before nontransactional MySQL DDL.
        var rows = legacy(ids);
        try (var ddl = connection.createStatement()) {
            ddl.execute("ALTER TABLE mf_factory "
                    + "ADD COLUMN runtime_mode VARCHAR(16) NOT NULL DEFAULT 'LOCAL' COMMENT '业务运行模式，独立MES或ERP集成',"
                    + "ADD COLUMN erp_provider VARCHAR(128) NULL COMMENT 'ERP供应商注册标识，本地模式为空',"
                    + "ADD COLUMN erp_config_ref VARCHAR(128) NULL COMMENT 'ERP连接配置不透明引用，不含凭证',"
                    + "ADD COLUMN integration_revision BIGINT NOT NULL DEFAULT 1 COMMENT '工厂业务集成配置版本',"
                    + "ADD COLUMN integration_locked BOOLEAN NOT NULL DEFAULT FALSE COMMENT '业务权威已锁定，普通编辑不得切换模式或绑定'");
        }
        try (var update = connection.prepareStatement("UPDATE mf_factory SET runtime_mode=?,erp_provider=?,erp_config_ref=?,integration_locked=TRUE WHERE factory_id=?")) {
            for (var row : rows) {
                update.setString(1, row.mode());
                update.setString(2, row.provider());
                update.setString(3, row.reference());
                update.setString(4, row.factoryId());
                if (update.executeUpdate() != 1) throw new SQLException("Factory changed during integration migration");
            }
        }
    }
}
