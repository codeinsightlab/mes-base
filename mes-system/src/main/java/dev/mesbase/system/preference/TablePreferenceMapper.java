package dev.mesbase.system.preference;

import org.apache.ibatis.annotations.Param;

/**
 * Infrastructure preference persistence; ownership is always supplied by the trusted Service.
 */
public interface TablePreferenceMapper {
    String selectColumns(@Param("owner") TablePreferenceOwner owner, @Param("key") TablePreferenceKey key);

    int upsert(@Param("owner") TablePreferenceOwner owner, @Param("key") TablePreferenceKey key,
               @Param("json") String json);

    int delete(@Param("owner") TablePreferenceOwner owner, @Param("key") TablePreferenceKey key);
}
