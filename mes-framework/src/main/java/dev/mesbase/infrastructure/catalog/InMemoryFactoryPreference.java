package dev.mesbase.infrastructure.catalog;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import dev.mesbase.core.factory.*;
import dev.mesbase.core.security.AuthenticatedActor;
import dev.mesbase.core.error.*;
/** Process-local UX hint. Restart discards preferences. */
public final class InMemoryFactoryPreference implements FactoryPreference {
    private final ConcurrentHashMap<String, Snapshot> values = new ConcurrentHashMap<>();
    public Snapshot snapshot(AuthenticatedActor actor) { return values.getOrDefault(actor.id(), new Snapshot(Optional.empty(), 0)); }
    public Optional<FactoryId> lastFactory(AuthenticatedActor actor) { return snapshot(actor).factoryId(); }
    public void saveVersioned(AuthenticatedActor actor, FactoryId id, long revision) {
        if (revision < 0 || revision == Long.MAX_VALUE) throw new FoundationException(ErrorCode.BAD_REQUEST);
        values.compute(actor.id(), (key, previous) -> {
            long current = 0;
            if (previous != null) current = previous.revision();
            if (current != revision) throw new FoundationException(ErrorCode.RESOURCE_CONFLICT);
            return new Snapshot(Optional.of(id), revision + 1);
        });
    }
}
