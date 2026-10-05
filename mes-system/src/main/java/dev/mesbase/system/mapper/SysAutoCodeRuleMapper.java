package dev.mesbase.system.mapper;

import dev.mesbase.common.core.domain.entity.SysAutoCodeRule;

import java.util.List;

@dev.mesbase.infrastructure.persistence.PersistenceOwner(dev.mesbase.infrastructure.persistence.PersistenceOwner.Kind.PLATFORM)
public interface SysAutoCodeRuleMapper {

    public List<SysAutoCodeRule> selectSysAutoCodeResultList(SysAutoCodeRule sysAutoCodeRule);

    public SysAutoCodeRule findById(Long ruleId);

    public int add(SysAutoCodeRule sysAutoCodeRule);

    public int updateSysAutoCodeRule(SysAutoCodeRule sysAutoCodeRule);

    public int deleteById(Long ruleId);

    public SysAutoCodeRule checkRuleCodeUnique(String ruleCode);

    public SysAutoCodeRule checkRuleNameUnique(String ruleName);
}
