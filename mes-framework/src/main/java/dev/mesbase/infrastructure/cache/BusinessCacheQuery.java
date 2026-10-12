package dev.mesbase.infrastructure.cache;

/**
 * Query DTOs retain every filter; these methods expose effective pagination and ordering.
 */
public interface BusinessCacheQuery {
    int cacheOffset();

    int cacheLimit();

    String cacheSort();
}
