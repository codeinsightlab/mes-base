package dev.mesbase.infrastructure.cache;
import dev.mesbase.core.factory.FactoryId;
import java.util.Optional;
import java.util.Objects;
/** Structured keys avoid delimiter collisions. Factory absence is legal only for PLATFORM. */
public record CacheKey(String environment, String enterprise, Kind kind, Optional<FactoryId> factory, String namespace, String key) {
    public enum Kind { PLATFORM, BUSINESS }
    public CacheKey {
        Objects.requireNonNull(environment); Objects.requireNonNull(enterprise); Objects.requireNonNull(kind); Objects.requireNonNull(factory); Objects.requireNonNull(namespace); Objects.requireNonNull(key);
        if((kind==Kind.BUSINESS)!=factory.isPresent()) throw new IllegalArgumentException("Cache scope mismatch");
    }
}
