package dev.mesbase.server.schema;

import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.springframework.core.env.Environment;

/**
 * All center migration entrypoints register the environment-aware legacy import in schema history.
 */
public final class FactoryFlyway {
    private FactoryFlyway() {
    }

    public static FluentConfiguration configure(DataSource source, Environment environment) {
        return Flyway.configure().dataSource(source).locations("classpath:db/platform", "classpath:db/system")
                .javaMigrations(new V34__FactoryBusinessIntegration(environment));
    }
}
