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
    @Transactional(transactionManager = "masterTransactionManager", rollbackFor = Exception.class)
    public void save(AuthenticatedActor actor, FactoryId factoryId) {
        long userId = userId(actor);
        int changed = preferences.save(new MfUserPreference(userId, factoryId.value()));
        if (changed == 1 || changed == 2) {
            return;
        }
        // MySQL may report zero for an unchanged upsert, depending on the driver setting.
        if (changed == 0 && Optional.ofNullable(preferences.selectById(userId))
                .map(MfUserPreference::lastFactoryId).filter(factoryId.value()::equals).isPresent()) {
            return;
        }
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
