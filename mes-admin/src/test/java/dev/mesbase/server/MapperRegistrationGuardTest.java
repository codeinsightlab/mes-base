package dev.mesbase.server;

import dev.mesbase.infrastructure.persistence.PersistenceOwner;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.mock.env.MockEnvironment;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MapperRegistrationGuardTest {
    @Test
    void rejectsDuplicateNamesBeforeRegistrationCanSilentlySkipThem() {
        rejects("Duplicate Mapper name", Map.of(
                PersistenceOwner.Kind.PLATFORM, List.of("dev.mesbase.persistencefixture.duplicate.first"),
                PersistenceOwner.Kind.BUSINESS, List.of("dev.mesbase.persistencefixture.duplicate.second")));
    }

    @Test
    void rejectsLegacyAnnotationConflictingWithPackageOwner() {
        rejects("Mapper annotation conflicts with package", Map.of(
                PersistenceOwner.Kind.BUSINESS, List.of("dev.mesbase.persistencefixture.conflict")));
    }

    private void register(Map<PersistenceOwner.Kind, List<String>> roots) {
        new OwnerMapperRegistration(new MockEnvironment(), roots)
                .postProcessBeanDefinitionRegistry(new DefaultListableBeanFactory());
    }

    private void rejects(String expected, Map<PersistenceOwner.Kind, List<String>> roots) {
        Throwable failure = assertThrows(RuntimeException.class, () -> register(roots));
        while (failure.getCause() != null) failure = failure.getCause();
        assertTrue(failure.getMessage().contains(expected), failure.toString());
    }
}
