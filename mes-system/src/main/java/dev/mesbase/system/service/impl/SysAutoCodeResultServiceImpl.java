package dev.mesbase.system.service.impl;

import dev.mesbase.common.core.domain.entity.SysAutoCodeResult;
import dev.mesbase.system.mapper.SysAutoCodeResultMapper;
import dev.mesbase.system.service.IAutoCodeResultService;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@ConditionalOnProperty(name="mes.system.enabled",havingValue="true")
public class SysAutoCodeResultServiceImpl implements IAutoCodeResultService {

    @Autowired
    private SysAutoCodeResultMapper sysAutoCodeResultMapper;

    @Override
    public List<SysAutoCodeResult> list(SysAutoCodeResult sysAutoCodeResult) {
        return sysAutoCodeResultMapper.selectSysAutoCodeResultList(sysAutoCodeResult);
    }

    @Override
    @Transactional(transactionManager="platformTransactionManager", rollbackFor=Exception.class)
    public void saveAutoCodeResult(SysAutoCodeResult sysAutoCodeResult) {
        sysAutoCodeResultMapper.add(sysAutoCodeResult);
    }

    @Override
    @Transactional(transactionManager="platformTransactionManager", rollbackFor=Exception.class)
    public void updateAutoCodeResult(SysAutoCodeResult sysAutoCodeResult) {
        sysAutoCodeResultMapper.updateAutoCodeResult(sysAutoCodeResult);
    }
}
