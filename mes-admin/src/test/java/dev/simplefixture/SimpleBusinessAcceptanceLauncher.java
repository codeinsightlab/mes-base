package dev.simplefixture;

import dev.MesBaseApplication;
import dev.mesbase.business.basic.sync.ExternalMaterial;
import dev.mesbase.business.basic.sync.MaterialQueryPort;
import dev.mesbase.business.basic.sync.UnconfiguredMaterialQueryAdapter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.springframework.boot.SpringApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

/**
 * TEST ONLY: local MySQL/browser acceptance. Never included in the production JAR.
 */
public class SimpleBusinessAcceptanceLauncher {
    public static void main(String[] args) {
        SpringApplication.run(new Class<?>[]{MesBaseApplication.class, TestSource.class}, args);
    }

    @Configuration
    public static class TestSource {
        @Bean
        MaterialQueryPort materialTestSource(Environment environment) {
            Path stage = Path.of(environment.getRequiredProperty("mes.simple-acceptance.stage"));
            return (scope, maximum) -> {
                if (!List.of("1").contains(scope.factoryId().value())) {
                    return new UnconfiguredMaterialQueryAdapter().query(scope, maximum);
                }
                String name;
                try {
                    name = Files.readString(stage).strip();
                } catch (Exception failure) {
                    throw new IllegalStateException("TEST ONLY source unavailable", failure);
                }
                return List.of(new ExternalMaterial("TEST-MES-001", name, "TEST ONLY", null,
                        1L, "ENABLED", "TEST-ERP-001", "ERP-TEST-M001"));
            };
        }
    }
}
