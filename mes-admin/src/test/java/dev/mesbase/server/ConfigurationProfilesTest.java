package dev.mesbase.server;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Parse/bind profiles without component scanning, database access, Redis or ERP requests.
 */
class ConfigurationProfilesTest {
    @Test
    void allApplicationProfilesAndExamplesParseWithoutDuplicateKeysOrRetiredModeDeclarations() throws Exception {
        var options = new LoaderOptions();
        options.setAllowDuplicateKeys(false);
        var yaml = new Yaml(options);
        for (Path folder : List.of(Path.of("mes-admin/src/main/resources"), Path.of("config"))) {
            try (var paths = Files.list(folder)) {
                for (Path file : paths.filter(path -> path.getFileName().toString()
                        .startsWith("application") && path.toString().endsWith(".yml")).toList()) {
                    Map<?, ?> parsed = yaml.load(Files.readString(file));
                    assertTrue(parsed != null, file.getFileName().toString());
                    check(parsed, "");
                }
            }
        }
    }

    private static void check(Map<?, ?> values, String prefix) {
        values.forEach((key, value) -> {
            String path = prefix.isEmpty() ? key.toString() : prefix + "." + key;
            assertFalse(path.equals("mes.integration.warehouse.factories"), "旧模式声明不能回到 Profile");
            assertFalse(path.equals("mes.integration.digiwin-e10"), "E10 参数归工厂中心配置");
            if (path.equals("user.password")) assertTrue(value instanceof Map, "密码策略不能被无效的标量覆盖");
            if (value instanceof Map<?, ?> nested) check(nested, path);
        });
    }

    @Test
    void devAndProdExampleProfilesBindWithoutStartingServices() {
        for (String profile : List.of("dev", "prod")) {
            String example = Path.of("config/application-" + profile + ".example.yml").toAbsolutePath().toUri()
                    .toString();
            new ApplicationContextRunner().withInitializer(new ConfigDataApplicationContextInitializer())
                    .withPropertyValues("spring.profiles.active=" + profile,
                            "spring.config.location=classpath:/application.yml," + example)
                    .run(context -> {
                        assertTrue(context.getStartupFailure() == null);
                        assertTrue(context.getEnvironment().getProperty("mes.runtime.enabled", Boolean.class, false));
                        assertFalse(context.containsBean("masterDataSource"));
                        assertFalse(context.containsBean("digiwinE10MoQueries"));
                    });
        }
    }
}
