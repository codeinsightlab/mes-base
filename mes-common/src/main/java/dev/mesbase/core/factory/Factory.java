package dev.mesbase.core.factory;
import java.util.Objects;
public record Factory(FactoryId id, String enterpriseId, String code, String name, FactoryStatus status) {
    public Factory { Objects.requireNonNull(id); Objects.requireNonNull(enterpriseId); Objects.requireNonNull(code); Objects.requireNonNull(name); Objects.requireNonNull(status); }
}
