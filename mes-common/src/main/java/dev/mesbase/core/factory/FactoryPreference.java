package dev.mesbase.core.factory;
import java.util.Optional;
import dev.mesbase.core.security.AuthenticatedActor;
/** UX hint only; never grants access or permission. */
public interface FactoryPreference {
    Optional<FactoryId> lastFactory(AuthenticatedActor actor);
    void save(AuthenticatedActor actor, FactoryId factoryId);
}
