package dev.mesbase.core.factory;
import java.util.Objects;
public record FactoryId(String value) {
    public FactoryId { Objects.requireNonNull(value); if (!value.matches("[A-Za-z0-9_-]{1,64}")) throw new IllegalArgumentException("Invalid factory identifier"); }
}
