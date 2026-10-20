package dev.issuefixture;

import dev.mesbase.server.managed.DatasourcePasswordCipher;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.util.Map;
import java.util.Arrays;
import org.flywaydb.core.Flyway;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.yaml.snakeyaml.Yaml;

/**
 * TEST ONLY: apply the next formal business migration to the explicitly designated existing DEV binding.
 */
public final class LegacyHistoryFlywayAcceptance {
    public static void main(String[] args) throws Exception {
        if (args.length != 3 && args.length != 4)
            throw new IllegalArgumentException("Expected private DEV config, evidence directory and target version");
        Map<?, ?> root = new Yaml().load(Files.readString(Path.of(args[0])));
        var mes = (Map<?, ?>) root.get("mes");
        var platform = (Map<?, ?>) mes.get("platform");
        var runtime = (Map<?, ?>) mes.get("runtime");
        var output = Path.of(args[1]);
        try (var center = DriverManager.getConnection((String) platform.get("url"), (String) platform.get("username"), (String) platform.get("password"));
             var query = center.prepareStatement("SELECT f.factory_id,f.runtime_mode,f.status,d.datasource_id,d.db_host,d.db_port,d.db_name,d.db_username,d.password_cipher,d.status FROM mf_factory f JOIN mf_datasource d USING(datasource_id) WHERE f.factory_id='TEST_DEV_PERF_20261019'")) {
            try (var result = query.executeQuery()) {
                if (!result.next() || !"LOCAL".equals(result.getString(2)) || !"ENABLED".equals(result.getString(3))
                        || !"9".equals(result.getString(4)) || !"192.168.18.25".equals(result.getString(5))
                        || result.getInt(6) != 3306 || !"mes_base_dev_perf_test".equals(result.getString(7)) || !"ENABLED".equals(result.getString(10)))
                    throw new IllegalStateException("DEV target binding mismatch");
                String password = new DatasourcePasswordCipher((String) runtime.get("datasource-key")).decrypt(result.getString(9));
                var source = new DriverManagerDataSource("jdbc:mysql://192.168.18.25:3306/mes_base_dev_perf_test?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai&sessionVariables=lock_wait_timeout=5", result.getString(8), password);
                try (var target = source.getConnection(); var statement = target.createStatement(); var identity = statement.executeQuery("SELECT @@server_uuid")) {
                    if (!identity.next() || !"03dbc952-7403-11ef-802c-0242ac130002".equals(identity.getString(1)))
                        throw new IllegalStateException("DEV instance mismatch");
                }
                var flyway = Flyway.configure().dataSource(source).locations("classpath:db/business")
                        .table("flyway_business_schema_history").target(args[2]).load();
                if (args.length == 4 && "--repair-failed-only".equals(args[3])) {
                    var failed = Arrays.stream(flyway.info().all()).filter(m -> m.getState().isFailed()).toList();
                    if (failed.size() != 1 || !args[2].equals(failed.getFirst().getVersion().getVersion()))
                        throw new IllegalStateException("Only this task's failed migration may be repaired");
                    var validation = flyway.validateWithResult();
                    if (validation.invalidMigrations.stream().anyMatch(m -> !args[2].equals(m.version)))
                        throw new IllegalStateException("Other published migration validation failed");
                    var checksums = Arrays.stream(flyway.info().all())
                            .filter(m -> m.getState().isApplied() && !m.getState().isFailed())
                            .map(m -> m.getVersion() + ":" + m.getChecksum()).toList();
                    flyway.repair();
                    var afterChecksums = Arrays.stream(flyway.info().all())
                            .filter(m -> m.getState().isApplied() && !m.getState().isFailed())
                            .map(m -> m.getVersion() + ":" + m.getChecksum()).toList();
                    if (!checksums.equals(afterChecksums)) throw new IllegalStateException("Published history changed");
                    Files.writeString(output.resolve("flyway-" + args[2] + "-repair.txt"), "Only failed migration record removed; all published checksums unchanged");
                    return;
                }
                var before = flyway.info().current();
                if (before == null || !String.valueOf(Integer.parseInt(args[2]) - 1)
                        .equals(before.getVersion().getVersion()))
                    throw new IllegalStateException("Unexpected predecessor version");
                Files.writeString(output.resolve("flyway-" + args[2] + "-before.txt"), before.getVersion() + " " + before.getChecksum());
                flyway.migrate();
                flyway.validate();
                Files.writeString(output.resolve("flyway-" + args[2] + "-after.txt"), flyway.info().current()
                        .getVersion() + " " + flyway.info().current().getChecksum());
            }
        }
    }
}
