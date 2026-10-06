package dev.mesbase.persistencefixture.business;

import dev.mesbase.infrastructure.persistence.PersistenceOwner;
import org.apache.ibatis.annotations.Select;

@PersistenceOwner(PersistenceOwner.Kind.BUSINESS)
public interface BusinessProbeMapper { @Select("SELECT label FROM marker") String label(); }
