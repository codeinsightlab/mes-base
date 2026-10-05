package dev.mesbase.core.factory;
import java.util.Objects;
public record Enterprise(String id, String name) { public Enterprise { Objects.requireNonNull(id); Objects.requireNonNull(name); } }
