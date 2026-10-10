package dev.mesbase.system.factory;

/**
 * Metadata query only: an enabled, bound business database is required for automatic selection.
 */
public interface ManagedFactoryExtraMapper {
    ManagedFactory defaultBusinessFactory();
}
