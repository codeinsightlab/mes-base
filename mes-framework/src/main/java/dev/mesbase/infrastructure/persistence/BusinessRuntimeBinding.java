package dev.mesbase.infrastructure.persistence;
import dev.mesbase.core.factory.FactoryId;
import java.util.Objects;
public record BusinessRuntimeBinding(FactoryId factoryId, DatasourceIdentity datasourceIdentity, long revision) {
    public BusinessRuntimeBinding { Objects.requireNonNull(factoryId); Objects.requireNonNull(datasourceIdentity); if(revision<1) throw new IllegalArgumentException("Invalid revision"); }
}
