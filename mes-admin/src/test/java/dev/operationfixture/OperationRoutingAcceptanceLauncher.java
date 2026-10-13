package dev.operationfixture;

import dev.MesBaseApplication;
import dev.mesbase.business.production.sync.ExternalOperation;
import dev.mesbase.business.production.sync.ExternalRouting;
import dev.mesbase.business.production.sync.ExternalRoutingStep;
import dev.mesbase.business.production.sync.OperationQueryPort;
import dev.mesbase.business.production.sync.RoutingQueryPort;
import dev.mesbase.business.production.sync.UnconfiguredOperationQueryAdapter;
import dev.mesbase.business.production.sync.UnconfiguredRoutingQueryAdapter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.springframework.boot.SpringApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

/**
 * TEST ONLY: real MySQL/Redis/HTTP/browser acceptance, synthetic external ports, absent from production JAR.
 */
public class OperationRoutingAcceptanceLauncher {
    public static void main(String[] args) {
        SpringApplication.run(new Class<?>[]{MesBaseApplication.class, TestPorts.class}, args);
    }

    @Configuration
    public static class TestPorts {
        private String stage(Environment environment) {
            try {
                return Files.readString(Path.of(environment.getRequiredProperty("mes.operation-acceptance.stage")))
                        .strip();
            } catch (Exception failure) {
                throw new IllegalStateException("TEST ONLY source unavailable", failure);
            }
        }

        @Bean
        OperationQueryPort testOperations(Environment environment) {
            return (scope, maximum) -> {
                if (!"1".equals(scope.factoryId().value())) {
                    return new UnconfiguredOperationQueryAdapter().query(scope, maximum);
                }
                return List.of(
                        new ExternalOperation("TEST-EXT-OP1", "TEST-SYNC-OP1", "TEST ONLY 工序 " + stage(environment), null, "ENABLED"),
                        new ExternalOperation("TEST-EXT-OP2", "TEST-SYNC-OP2", "TEST ONLY 第二工序", null, "ENABLED"));
            };
        }

        @Bean
        RoutingQueryPort testRoutes(Environment environment) {
            return (scope, maximum) -> {
                if (!"1".equals(scope.factoryId().value())) {
                    return new UnconfiguredRoutingQueryAdapter().query(scope, maximum);
                }
                return List.of(new ExternalRouting("TEST-EXT-ROUTE", "TEST-SYNC-ROUTE",
                        "TEST ONLY 路线 " + stage(environment), null, stage(environment),
                        List.of("TEST-OR-PRODUCT"),
                        List.of(new ExternalRoutingStep(1, "TEST-SYNC-OP1", true, null),
                                new ExternalRoutingStep(2, "TEST-SYNC-OP2", false, null))));
            };
        }
    }
}
