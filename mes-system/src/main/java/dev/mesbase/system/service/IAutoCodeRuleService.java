package dev.mesbase.system.service;

import dev.mesbase.common.core.domain.entity.SysAutoCodeRule;
import dev.mesbase.system.dto.AutoCodeRuleDocument;

import java.util.List;

public interface IAutoCodeRuleService {

    AutoCodeRuleDocument document(Long ruleId);

    AutoCodeRuleDocument createDocument(SysAutoCodeRule rule, String prefix, String actor);

    AutoCodeRuleDocument updateDocument(SysAutoCodeRule rule, String prefix, String version, String actor);

    public SysAutoCodeRule getOne(String ruleCode);

    public List<SysAutoCodeRule> selectAutoCodeList(SysAutoCodeRule sysAutoCodeRule);

    public SysAutoCodeRule findById(Long ruleId);

    public String checkRuleCodeUnique(SysAutoCodeRule sysAutoCodeRule);

    public String checkRuleNameUnique(SysAutoCodeRule sysAutoCodeRule);

    public int insertInfo(SysAutoCodeRule rule);

    public int updateInfo(SysAutoCodeRule rule);

    public int deleteById(Long ruleId);

    public int deleteByIds(Long[] ruleIds);

}
