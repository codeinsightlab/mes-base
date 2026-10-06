package dev.mesbase.system.mapper;
import dev.mesbase.common.core.domain.entity.SysUser;
import dev.mesbase.system.domain.dto.UserNamesQuery;
import dev.mesbase.system.domain.dto.UserLookupQuery;
import java.util.List;
/** 批量用户名称补全；通用 Mapper 的单用户名 LIKE 查询不能表达该条件。 */
@dev.mesbase.infrastructure.persistence.PersistenceOwner(dev.mesbase.infrastructure.persistence.PersistenceOwner.Kind.PLATFORM)
public interface SysUserDisplayExtraMapper {
    List<SysUser> selectByUserNames(UserNamesQuery query);
    List<SysUser> selectPersonnelUsers(UserLookupQuery query);
    List<Long> selectPersonnelUserIdsByKeyword(UserLookupQuery query);
}
