package dev.mesbase.server.schema;

import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.configuration.FluentConfiguration;

/**
 * Center schema starts from the current V1 initialization; legacy upgrades are not runtime migrations.
 */
public final class FactoryFlyway {
    private FactoryFlyway() {
    }

    public static FluentConfiguration configure(DataSource source) {
        return Flyway.configure().dataSource(source).locations("classpath:db/center")
                .cleanDisabled(true).baselineOnMigrate(false).outOfOrder(false)
                .validateOnMigrate(true).validateMigrationNaming(true);
    }
}
