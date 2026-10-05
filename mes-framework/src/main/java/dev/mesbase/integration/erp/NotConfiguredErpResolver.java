package dev.mesbase.integration.erp;
import dev.mesbase.core.factory.FactoryScope;
import java.util.Objects;
public final class NotConfiguredErpResolver implements FactoryErpConnectionResolver {
    public ErpConnectionBinding resolve(FactoryScope scope) { Objects.requireNonNull(scope); throw new IntegrationNotConfiguredException(); }
}
