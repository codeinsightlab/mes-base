package dev.mesbase.core.factory;
import java.util.Optional;
import dev.mesbase.core.security.AuthenticatedActor;
/** UX hint only; never grants access or permission. */
public interface FactoryPreference {
    record Snapshot(Optional<FactoryId> factoryId, long revision) {}
    Optional<FactoryId> lastFactory(AuthenticatedActor actor);
    Snapshot snapshot(AuthenticatedActor actor);
    void saveVersioned(AuthenticatedActor actor, FactoryId factoryId, long revision);
}
