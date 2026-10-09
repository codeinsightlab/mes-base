package dev.mesbase.persistencefixture.conflict;

import dev.mesbase.infrastructure.persistence.PersistenceOwner;

/**
 * TEST ONLY old owner annotation conflicting with its configured package owner.
 */
@PersistenceOwner(PersistenceOwner.Kind.PLATFORM)
public interface ConflictingMapper {
}
