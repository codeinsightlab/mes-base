package dev.mesbase.system.mapper;
import java.util.List;
import dev.mesbase.common.core.domain.entity.SysUser;
import dev.mesbase.system.domain.SysUserRecipientQuery;
@dev.mesbase.infrastructure.persistence.PersistenceOwner(dev.mesbase.infrastructure.persistence.PersistenceOwner.Kind.PLATFORM)
public interface SysUserRecipientExtraMapper { List<SysUser> selectUsers(SysUserRecipientQuery query); }
