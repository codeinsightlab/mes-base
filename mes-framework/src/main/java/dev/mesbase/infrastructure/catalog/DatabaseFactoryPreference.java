package dev.mesbase.infrastructure.catalog;

import dev.mesbase.common.core.domain.model.LoginUser;
import dev.mesbase.core.error.ErrorCode;
import dev.mesbase.core.error.FoundationException;
import dev.mesbase.core.factory.FactoryId;
import dev.mesbase.core.factory.FactoryPreference;
import dev.mesbase.core.security.AuthenticatedActor;
import dev.mesbase.system.factory.MfUserPreference;
import dev.mesbase.system.factory.MfUserPreferenceMapper;
import java.util.Optional;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;

/**
 * SOURCE_PRODUCT_PORT: durable preference, always owned by the trusted authenticated user ID.
 */
public class DatabaseFactoryPreference implements FactoryPreference {
    private final MfUserPreferenceMapper preferences;

    public DatabaseFactoryPreference(MfUserPreferenceMapper preferences) {
        this.preferences = preferences;
    }

    @Override
    @Transactional(transactionManager = "masterTransactionManager", readOnly = true)
    public Optional<FactoryId> lastFactory(AuthenticatedActor actor) {
        return Optional.ofNullable(preferences.selectById(userId(actor)))
                .map(MfUserPreference::lastFactoryId).map(FactoryId::new);
    }

    @Override
    @Transactional(transactionManager = "masterTransactionManager", readOnly = true)
    public Snapshot snapshot(AuthenticatedActor actor) {
        var row = preferences.selectById(userId(actor));
        if (row == null) return new Snapshot(Optional.empty(), 0);
        return new Snapshot(Optional.of(new FactoryId(row.lastFactoryId())), row.revision());
    }

    @Override
    @Transactional(transactionManager = "masterTransactionManager", rollbackFor = Exception.class)
    public void saveVersioned(AuthenticatedActor actor, FactoryId factoryId, long revision) {
        if (revision < 0 || revision == Long.MAX_VALUE) throw new FoundationException(ErrorCode.BAD_REQUEST);
        var row = new MfUserPreference(userId(actor), factoryId.value(), revision);
        // Claim an absent row first, avoiding competing UPDATE-gap-lock -> INSERT upgrades.
        if (revision == 0 && preferences.insertInitial(row) == 1) return;
        if (preferences.updateVersioned(row) == 1) return;
        throw new FoundationException(ErrorCode.RESOURCE_CONFLICT);
    }

    private long userId(AuthenticatedActor actor) {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof LoginUser user)
                || user.getUserId() == null || !actor.id().equals(user.getUsername())) {
            throw new FoundationException(ErrorCode.UNAUTHENTICATED);
        }
        return user.getUserId();
    }
}
