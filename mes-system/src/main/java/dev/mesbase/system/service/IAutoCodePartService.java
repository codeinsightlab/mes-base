package dev.mesbase.system.service;

import dev.mesbase.common.core.domain.entity.SysAutoCodePart;

import java.util.List;

public interface IAutoCodePartService {

    public List<SysAutoCodePart> listPart(SysAutoCodePart sysAutoCodePart);

    public SysAutoCodePart findById(Long partId);

    public String checkPartUnique(SysAutoCodePart sysAutoCodePart);


    public int insertPart(SysAutoCodePart sysAutoCodePart);

    public int updatePart(SysAutoCodePart sysAutoCodePart);

    public int deleteByIds(Long[] partIds);

}
