package dev.mesbase.framework.persistence;

import dev.mesbase.common.core.domain.entity.SysUser;
import dev.mesbase.infrastructure.persistence.PlatformPersistence;
import dev.mesbase.system.domain.dto.UserLookupQuery;
import dev.mesbase.system.mapper.SysUserDisplayExtraMapper;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name="mes.analytics.enabled",havingValue="true")
public class SystemUserLookup {
    private final PlatformPersistence platform;
    public SystemUserLookup(PlatformPersistence platform) { this.platform = platform; }
    public List<Long> selectPersonnelUserIdsByKeyword(UserLookupQuery query) { return platform.readOnly(s -> s.mapper(SysUserDisplayExtraMapper.class).selectPersonnelUserIdsByKeyword(query)); }
    public List<SysUser> selectPersonnelUsers(UserLookupQuery query) { return platform.readOnly(s -> s.mapper(SysUserDisplayExtraMapper.class).selectPersonnelUsers(query)); }
}
