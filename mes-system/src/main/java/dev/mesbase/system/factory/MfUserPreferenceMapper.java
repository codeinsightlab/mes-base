package dev.mesbase.system.factory;

import org.apache.ibatis.annotations.Param;

/**
 * Center database only; no permission decisions or user-supplied identity.
 */
public interface MfUserPreferenceMapper {
    MfUserPreference selectById(@Param("userId") long userId);

    int save(MfUserPreference preference);
}
