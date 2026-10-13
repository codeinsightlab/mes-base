package dev.mesbase.acceptance;

import dev.mesbase.server.managed.DatasourcePasswordCipher;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.util.Map;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.yaml.snakeyaml.Yaml;

/** TEST ONLY: resolve and verify the user-designated existing SIMPLE_B binding. */
public final class AcceptanceBusinessDatabase {
    private AcceptanceBusinessDatabase() {
    }

    public static DriverManagerDataSource source() throws Exception {
        Map<?, ?> root = new Yaml().load(Files.readString(Path.of("mes-admin/src/main/resources/application-dev.yml")));
        Map<?, ?> mes = (Map<?, ?>) root.get("mes");
        Map<?, ?> platform = (Map<?, ?>) mes.get("platform");
        Map<?, ?> runtime = (Map<?, ?>) mes.get("runtime");
        try (var center = DriverManager.getConnection((String) platform.get("url"),
                (String) platform.get("username"), (String) platform.get("password"));
             var query = center.prepareStatement("SELECT d.datasource_id,d.db_host,d.db_port,d.db_name,"
                     + "d.db_username,d.password_cipher,f.status,d.status FROM mf_factory f "
                     + "JOIN mf_datasource d ON d.datasource_id=f.datasource_id WHERE f.factory_id='SIMPLE_B'")) {
            try (var binding = query.executeQuery()) {
                if (!binding.next() || !"192.168.18.25".equals(binding.getString(2))
                        || binding.getInt(3) != 3306
                        || !"mes_base_dev_business_simple_b".equals(binding.getString(4))
                        || !"ENABLED".equals(binding.getString(7)) || !"ENABLED".equals(binding.getString(8))) {
                    throw new IllegalStateException("The configured SIMPLE_B acceptance binding does not match");
                }
                String password = new DatasourcePasswordCipher((String) runtime.get("datasource-key"))
                        .decrypt(binding.getString(6));
                return new DriverManagerDataSource("jdbc:mysql://" + binding.getString(2) + ":"
                        + binding.getInt(3) + "/" + binding.getString(4)
                        + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai",
                        binding.getString(5), password);
            }
        }
    }
}
