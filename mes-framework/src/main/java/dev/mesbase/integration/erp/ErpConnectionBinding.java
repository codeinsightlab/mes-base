package dev.mesbase.integration.erp;
import dev.mesbase.core.factory.FactoryId;
import java.util.Objects;
/** Opaque connection reference only; credentials belong to future adapter configuration. */
public record ErpConnectionBinding(FactoryId factoryId, ErpProvider provider, String connectionIdentity, long revision) {
    public ErpConnectionBinding { Objects.requireNonNull(factoryId); Objects.requireNonNull(provider); Objects.requireNonNull(connectionIdentity); if(revision<1) throw new IllegalArgumentException("Invalid revision"); }
}
