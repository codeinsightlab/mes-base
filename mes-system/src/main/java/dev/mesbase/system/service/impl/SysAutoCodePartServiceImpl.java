package dev.mesbase.system.service.impl;

import dev.mesbase.common.constant.UserConstants;
import dev.mesbase.common.core.domain.entity.SysAutoCodePart;
import dev.mesbase.common.exception.ServiceException;
import dev.mesbase.system.mapper.AutoCodeExtraMapper;
import dev.mesbase.system.mapper.SysAutoCodePartMapper;
import dev.mesbase.system.mapper.SysAutoCodeRuleMapper;
import dev.mesbase.system.service.IAutoCodePartService;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Source SysAutoCodePartServiceImpl PORT; rule ownership and serialized edits. */
@Service
@ConditionalOnProperty(name="mes.system.enabled",havingValue="true")
public class SysAutoCodePartServiceImpl implements IAutoCodePartService {
 private final SysAutoCodePartMapper mapper;private final SysAutoCodeRuleMapper rules;private final AutoCodeExtraMapper extra;
 public SysAutoCodePartServiceImpl(SysAutoCodePartMapper mapper,SysAutoCodeRuleMapper rules,AutoCodeExtraMapper extra){this.mapper=mapper;this.rules=rules;this.extra=extra;}
 public List<SysAutoCodePart> listPart(SysAutoCodePart query){return mapper.selectSysAutoCodePartList(query);}
 public SysAutoCodePart findById(Long id){var p=mapper.findById(id);if(p==null)throw new ServiceException("规则组成不存在");return p;}
 public String checkPartUnique(SysAutoCodePart p){return mapper.checkPartUnique(p)==null?UserConstants.UNIQUE:UserConstants.NOT_UNIQUE;}
 @Transactional(transactionManager="platformTransactionManager", rollbackFor=Exception.class)
 public int insertPart(SysAutoCodePart p){p.setPartId(null);prepare(p);return mapper.add(p);}
 @Transactional(transactionManager="platformTransactionManager", rollbackFor=Exception.class)
 public int updatePart(SysAutoCodePart p){var old=findById(p.getPartId());if(!Objects.equals(old.getRuleId(),p.getRuleId()))throw new ServiceException("不能变更所属规则");prepare(p);return mapper.updateSysAutoCodePart(p);}
 @Transactional(transactionManager="platformTransactionManager", rollbackFor=Exception.class)
 public int deleteByIds(Long[] ids){if(ids==null||ids.length==0||ids.length>100)throw new ServiceException("请选择规则组成");int count=0;for(Long id:Arrays.stream(ids).distinct().sorted().toList()){var p=findById(id);extra.lockRule(p.getRuleId());count+=mapper.deleteById(id);}return count;}
 private void prepare(SysAutoCodePart p){if(p.getRuleId()==null||rules.findById(p.getRuleId())==null)throw new ServiceException("所属规则不存在");extra.lockRule(p.getRuleId());validate(p);if(UserConstants.NOT_UNIQUE.equals(checkPartUnique(p)))throw new ServiceException("组成编码、名称或顺序重复");if("SERIALNO".equals(p.getPartType())){var q=new SysAutoCodePart();q.setRuleId(p.getRuleId());q.setPartType("SERIALNO");if(listPart(q).stream().anyMatch(other->!Objects.equals(other.getPartId(),p.getPartId())))throw new ServiceException("一个规则只能有一个流水号组成");}p.setSeriaNowNo(null);}
 private void validate(SysAutoCodePart p){if(p.getPartCode()==null||!p.getPartCode().matches("[A-Za-z][A-Za-z0-9_-]{0,63}")||p.getPartName()==null||p.getPartName().isBlank()||p.getPartName().length()>255||p.getPartIndex()==null||p.getPartIndex()<1||p.getPartIndex()>999||p.getPartLength()==null||p.getPartLength()<1||p.getPartLength()>64||p.getPartType()==null)throw new ServiceException("组成编码、名称、顺序或长度不符合要求");switch(p.getPartType()){
 case "INPUTCHAR" -> {p.setFixCharacter(null);p.setDateFormat(null);}
 case "FIXCHAR" -> {if(p.getFixCharacter()==null||p.getFixCharacter().length()!=p.getPartLength())throw new ServiceException("固定字符与组成长度不一致");}
 case "NOWDATE" -> {if(p.getDateFormat()==null||!Set.of("yyyy","yyyyMM","yyyyMMdd","yyyyMMddHH","yyyyMMddHHmm","yyyyMMddHHmmss","yyMMdd").contains(p.getDateFormat())||DateTimeFormatter.ofPattern(p.getDateFormat()).format(LocalDateTime.now()).length()!=p.getPartLength())throw new ServiceException("日期格式或组成长度不符合要求");}
 case "SERIALNO" -> {if(p.getSeriaStartNo()==null||p.getSeriaStartNo()<0||p.getSeriaStep()==null||p.getSeriaStep()<1||p.getCycleFlag()==null||!Set.of("Y","N").contains(p.getCycleFlag())||"Y".equals(p.getCycleFlag())&&(p.getCycleMethod()==null||!Set.of("YEAR","MONTH","DAY","HOUR","MINUTE","OTHER").contains(p.getCycleMethod())))throw new ServiceException("流水号起始、步长或循环设置不符合要求");}
 default -> throw new ServiceException("不支持的规则组成类型");
 }if(p.getRemark()!=null&&p.getRemark().length()>500)throw new ServiceException("备注过长");}
}
