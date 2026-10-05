package dev.mesbase.core.factory;
import java.util.Objects;
public record FactoryScope(FactoryId factoryId) { public FactoryScope { Objects.requireNonNull(factoryId); } }
