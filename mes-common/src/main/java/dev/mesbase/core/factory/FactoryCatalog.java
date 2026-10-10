package dev.mesbase.core.factory;

import java.util.Optional;
import java.util.List;

public interface FactoryCatalog {
    Optional<Factory> find(FactoryId id);

    /**
     * Default from eligible business bindings; empty catalogs cannot manufacture a database.
     */
    default Optional<Factory> defaultFactory() {
        return Optional.empty();
    }

    List<Factory> enabled(int offset, int limit);
}
