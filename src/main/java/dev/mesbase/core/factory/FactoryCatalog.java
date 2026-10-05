package dev.mesbase.core.factory;
import java.util.Optional;
import java.util.List;
public interface FactoryCatalog {
    Optional<Factory> find(FactoryId id);
    List<Factory> enabled(int offset, int limit);
}
