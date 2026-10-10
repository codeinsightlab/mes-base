package dev.mesbase.core.factory;

import java.util.List;
import java.util.Optional;
import dev.mesbase.core.security.AuthenticatedActor;
import dev.mesbase.core.error.ErrorCode;
import dev.mesbase.core.error.FoundationException;
import java.util.Objects;

public final class FactorySelectionService {
    private final FactoryCatalog catalog;
    private final FactoryPreference preference;

    public FactorySelectionService(FactoryCatalog catalog, FactoryPreference preference) {
        this.catalog = catalog;
        this.preference = preference;
    }

    public FactoryScope select(AuthenticatedActor actor, FactoryId id) {
        Objects.requireNonNull(actor);
        Factory factory = catalog.find(id).orElseThrow(() -> new FoundationException(ErrorCode.FACTORY_NOT_FOUND));
        if (factory.status() != FactoryStatus.ENABLED) throw new FoundationException(ErrorCode.FACTORY_DISABLED);
        return new FactoryScope(factory.id());
    }

    public Optional<FactoryScope> resolve(AuthenticatedActor actor, Optional<FactoryId> explicit) {
        if (explicit.isPresent()) return Optional.of(select(actor, explicit.get()));
        Optional<FactoryScope> preferred = preference.lastFactory(actor).flatMap(catalog::find)
                .filter(f -> f.status() == FactoryStatus.ENABLED).map(f -> new FactoryScope(f.id()));
        return preferred.isPresent() ? preferred : catalog.defaultFactory().map(f -> select(actor, f.id()));
    }

    public FactoryScope remember(AuthenticatedActor actor, FactoryId id) {
        FactoryScope scope = select(actor, id);
        preference.save(actor, id);
        return scope;
    }

    public List<Factory> options(int offset, int limit) {
        if (offset < 0 || limit < 1 || limit > 100) throw new FoundationException(ErrorCode.BAD_REQUEST);
        return catalog.enabled(offset, limit);
    }
}
