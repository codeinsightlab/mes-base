package dev.mesbase;

import dev.mesbase.core.error.FoundationException;
import dev.mesbase.core.factory.Factory;
import dev.mesbase.core.factory.FactoryId;
import dev.mesbase.core.factory.FactoryPreference;
import dev.mesbase.core.factory.FactorySelectionService;
import dev.mesbase.core.factory.FactoryStatus;
import dev.mesbase.core.security.AuthenticatedActor;
import dev.mesbase.core.security.Permission;
import dev.mesbase.core.security.Role;
import dev.mesbase.infrastructure.cache.BusinessCache;
import dev.mesbase.infrastructure.cache.PlatformCache;
import dev.mesbase.infrastructure.catalog.InMemoryFactoryCatalog;
import dev.mesbase.infrastructure.catalog.InMemoryFactoryPreference;
import dev.mesbase.infrastructure.security.InMemoryRoleAuthorization;
import dev.mesbase.platform.execution.FactoryContext;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuthorizationPreferenceCacheTest {
    @AfterEach
    void clean() {
        FactoryContext.clear();
    }

    private FactorySelectionService selection(FactoryPreference preferences) {
        return new FactorySelectionService(new InMemoryFactoryCatalog(List.of(
                new Factory(RuntimeFixture.A.factoryId(), "synthetic-enterprise", "A", "TEST ONLY A", FactoryStatus.ENABLED),
                new Factory(RuntimeFixture.B.factoryId(), "synthetic-enterprise", "B", "TEST ONLY B", FactoryStatus.ENABLED),
                new Factory(new FactoryId("C"), "synthetic-enterprise", "C", "TEST ONLY disabled", FactoryStatus.DISABLED))), preferences);
    }

    @Test
    void noRolesCanSelectEnabledFactoryButCannotPerformAuthorizedCapability() {
        var actor = new AuthenticatedActor("test-only-no-role");
        var service = selection(new InMemoryFactoryPreference());
        var auth = new InMemoryRoleAuthorization();
        assertEquals(RuntimeFixture.B, service.select(actor, RuntimeFixture.B.factoryId()));
        assertFalse(auth.hasPermission(actor, RuntimeFixture.B, new Permission("foundation:access:read")));
    }

    @Test
    void grantsRemainIndependentAndRevocationIsImmediateForNextLookup() {
        var actor = new AuthenticatedActor("test-only-role-A");
        var permission = new Permission("foundation:access:read");
        var auth = new InMemoryRoleAuthorization();
        auth.replace(actor, List.of(new Role("test-role", RuntimeFixture.A.factoryId(), Set.of(permission))));
        assertTrue(auth.hasPermission(actor, RuntimeFixture.A, permission));
        assertFalse(auth.hasPermission(actor, RuntimeFixture.B, permission));
        auth.replace(actor, List.of());
        assertFalse(auth.hasPermission(actor, RuntimeFixture.A, permission));
    }

    @Test
    void catalogWithoutEligibleBusinessBindingHasNoDefault() {
        var service = selection(new InMemoryFactoryPreference());
        assertTrue(service.resolve(new AuthenticatedActor("test-only-user"), Optional.empty()).isEmpty());
    }

    @Test
    void explicitFactoryOverridesValidPreference() {
        var prefs = new InMemoryFactoryPreference();
        var actor = new AuthenticatedActor("test-only-user");
        prefs.save(actor, RuntimeFixture.A.factoryId());
        var service = selection(prefs);
        assertEquals(Optional.of(RuntimeFixture.A), service.resolve(actor, Optional.empty()));
        assertEquals(Optional.of(RuntimeFixture.B), service.resolve(actor, Optional.of(RuntimeFixture.B.factoryId())));
    }

    @Test
    void missingDisabledPreferenceIsIgnoredButInvalidExplicitChoiceFails() {
        var prefs = new InMemoryFactoryPreference();
        var actor = new AuthenticatedActor("test-only-user");
        var service = selection(prefs);
        for (String id : List.of("C", "missing")) {
            prefs.save(actor, new FactoryId(id));
            assertTrue(service.resolve(actor, Optional.empty()).isEmpty());
            assertThrows(FoundationException.class, () -> service.resolve(actor, Optional.of(new FactoryId(id))));
        }
    }

    @Test
    void preferenceDoesNotCreateGrantAndOptionsHaveBound() {
        var prefs = new InMemoryFactoryPreference();
        var actor = new AuthenticatedActor("test-only-user");
        var service = selection(prefs);
        assertEquals(RuntimeFixture.B, service.remember(actor, RuntimeFixture.B.factoryId()));
        assertFalse(new InMemoryRoleAuthorization().hasPermission(actor, RuntimeFixture.B, new Permission("foundation:access:read")));
        assertThrows(FoundationException.class, () -> service.options(0, 101));
    }

    @Test
    void businessKeysAreScopedAndNeverGlobalFallback() {
        var business = new BusinessCache("test-only", "synthetic-enterprise");
        assertNotEquals(business.key(RuntimeFixture.A, "probe", "same"), business.key(RuntimeFixture.B, "probe", "same"));
        assertThrows(FoundationException.class, () -> business.key(null, "probe", "same"));
        assertThrows(FoundationException.class, () -> business.currentKey("probe", "same"));
        assertNotEquals(new PlatformCache("test-only", "synthetic-enterprise").key("probe", "same"), business.key(RuntimeFixture.A, "probe", "same"));
    }
}
