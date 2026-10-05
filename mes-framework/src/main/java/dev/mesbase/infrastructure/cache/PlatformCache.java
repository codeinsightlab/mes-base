package dev.mesbase.infrastructure.cache;
import java.util.Optional;
public record PlatformCache(String environment,String enterprise) {
    public CacheKey key(String namespace,String key) { return new CacheKey(environment,enterprise,CacheKey.Kind.PLATFORM,Optional.empty(),namespace,key); }
}
