package dev.mesbase.system.service.impl;

import dev.mesbase.common.core.domain.entity.SysAutoCodeResult;
import dev.mesbase.system.mapper.SysAutoCodeResultMapper;
import dev.mesbase.system.service.IAutoCodeResultService;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name="mes.system.enabled",havingValue="true")
public class SysAutoCodeResultServiceImpl implements IAutoCodeResultService {

    @Autowired
    private SysAutoCodeResultMapper sysAutoCodeResultMapper;

    @Override
    public List<SysAutoCodeResult> list(SysAutoCodeResult sysAutoCodeResult) {
        return sysAutoCodeResultMapper.selectSysAutoCodeResultList(sysAutoCodeResult);
    }

    @Override
    public void saveAutoCodeResult(SysAutoCodeResult sysAutoCodeResult) {
        sysAutoCodeResultMapper.add(sysAutoCodeResult);
    }

    @Override
    public void updateAutoCodeResult(SysAutoCodeResult sysAutoCodeResult) {
        sysAutoCodeResultMapper.updateAutoCodeResult(sysAutoCodeResult);
    }
}
