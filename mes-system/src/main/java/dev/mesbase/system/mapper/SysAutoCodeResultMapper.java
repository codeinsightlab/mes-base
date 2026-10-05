package dev.mesbase.system.mapper;

import dev.mesbase.common.core.domain.entity.SysAutoCodeResult;

import java.util.List;

@dev.mesbase.infrastructure.persistence.PersistenceOwner(dev.mesbase.infrastructure.persistence.PersistenceOwner.Kind.PLATFORM)
public interface SysAutoCodeResultMapper {
    public List<SysAutoCodeResult> selectSysAutoCodeResultList(SysAutoCodeResult sysAutoCodeResult);

    public void add(SysAutoCodeResult sysAutoCodeResult);

    public void updateAutoCodeResult(SysAutoCodeResult sysAutoCodeResult);

    public void deleteById(Long codeId);


}
