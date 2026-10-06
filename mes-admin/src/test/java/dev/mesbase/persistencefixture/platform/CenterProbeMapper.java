package dev.mesbase.persistencefixture.platform;

import dev.mesbase.infrastructure.persistence.PersistenceOwner;
import org.apache.ibatis.annotations.Select;

@PersistenceOwner(PersistenceOwner.Kind.PLATFORM)
public interface CenterProbeMapper { @Select("SELECT label FROM marker") String label(); }
