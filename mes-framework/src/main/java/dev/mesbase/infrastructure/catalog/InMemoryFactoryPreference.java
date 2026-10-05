package dev.mesbase.infrastructure.catalog;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import dev.mesbase.core.factory.*;
import dev.mesbase.core.security.AuthenticatedActor;
/** Process-local UX hint. Restart discards preferences. */
public final class InMemoryFactoryPreference implements FactoryPreference {
    private final ConcurrentHashMap<String,FactoryId> values=new ConcurrentHashMap<>();
    public Optional<FactoryId> lastFactory(AuthenticatedActor actor) { return Optional.ofNullable(values.get(actor.id())); }
    public void save(AuthenticatedActor actor,FactoryId id) { values.put(actor.id(),id); }
}
