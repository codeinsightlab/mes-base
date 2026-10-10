package dev.mesbase.system.mapper;

import dev.mesbase.common.core.domain.entity.SysUser;
import java.util.List;
import org.apache.ibatis.annotations.Param;

/**
 * Password-free request admission projection plus durable, user-indexed revocation delivery.
 */
public interface SessionAccountMapper {
    SysUser account(long userId);

    long revision(long userId);

    List<Revocation> revisions(@Param("ids") List<Long> ids);

    Long userId(String username);

    int enqueue(@Param("ids") List<Long> ids);

    List<Revocation> pending();

    int delivered(@Param("userId") long userId, @Param("revision") long revision);

    int failed(@Param("userId") long userId, @Param("revision") long revision,
               @Param("delaySeconds") long delaySeconds);

    record Revocation(long userId, long revision, int attempts) {
    }
}
