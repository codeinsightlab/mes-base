package dev.mesbase.infrastructure.persistence;

import java.util.Objects;

/** Trusted code registration; no class names or package scanning from configuration. */
public record BusinessMapperRegistration(Class<?> mapper) {
    public BusinessMapperRegistration {
        Objects.requireNonNull(mapper);
        var owner = mapper.getAnnotation(PersistenceOwner.class);
        if (owner == null || owner.value() != PersistenceOwner.Kind.BUSINESS)
            throw new IllegalArgumentException("Explicit BUSINESS mapper ownership required");
    }
}
