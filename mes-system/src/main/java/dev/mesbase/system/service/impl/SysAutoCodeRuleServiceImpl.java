package dev.mesbase.system.service.impl;

import dev.mesbase.common.constant.UserConstants;
import dev.mesbase.common.core.domain.entity.SysAutoCodeRule;
import dev.mesbase.common.exception.ServiceException;
import dev.mesbase.system.mapper.AutoCodeExtraMapper;
import dev.mesbase.system.mapper.SysAutoCodeRuleMapper;
import dev.mesbase.system.service.IAutoCodeRuleService;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Source SysAutoCodeRuleServiceImpl PORT; validation and reference guards merged.
 */
@Service
@ConditionalOnProperty(name = "mes.system.enabled", havingValue = "true")
public class SysAutoCodeRuleServiceImpl implements IAutoCodeRuleService {
    private final SysAutoCodeRuleMapper mapper;
    private final AutoCodeExtraMapper extra;
    private final AutoCodeRuleSnapshotCache cache;

    public SysAutoCodeRuleServiceImpl(SysAutoCodeRuleMapper mapper, AutoCodeExtraMapper extra,
                                     AutoCodeRuleSnapshotCache cache) {
        this.mapper = mapper;
        this.extra = extra;
        this.cache = cache;
    }

    public SysAutoCodeRule getOne(String code) {
        var q = new SysAutoCodeRule();
        q.setRuleCode(code);
        var rows = mapper.selectSysAutoCodeResultList(q);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public List<SysAutoCodeRule> selectAutoCodeList(SysAutoCodeRule query) {
        return mapper.selectSysAutoCodeResultList(query);
    }

    public SysAutoCodeRule findById(Long id) {
        var row = mapper.findById(id);
        if (row == null) throw new ServiceException("编码规则不存在");
        return row;
    }

    public String checkRuleCodeUnique(SysAutoCodeRule rule) {
        var old = mapper.checkRuleCodeUnique(rule.getRuleCode());
        return old != null && !Objects.equals(old.getRuleId(), rule.getRuleId()) ? UserConstants.NOT_UNIQUE : UserConstants.UNIQUE;
    }

    public String checkRuleNameUnique(SysAutoCodeRule rule) {
        var old = mapper.checkRuleNameUnique(rule.getRuleName());
        return old != null && !Objects.equals(old.getRuleId(), rule.getRuleId()) ? UserConstants.NOT_UNIQUE : UserConstants.UNIQUE;
    }

    @Transactional(transactionManager = "masterTransactionManager", rollbackFor = Exception.class)
    public int insertInfo(SysAutoCodeRule rule) {
        validate(rule);
        unique(rule);
        rule.setRuleId(null);
        int changed = mapper.add(rule);
        if (changed > 0) cache.invalidateAfterCommit();
        return changed;
    }

    @Transactional(transactionManager = "masterTransactionManager", rollbackFor = Exception.class)
    public int updateInfo(SysAutoCodeRule rule) {
        var old = findById(rule.getRuleId());
        extra.lockRule(old.getRuleId());
        if (!Objects.equals(old.getRuleCode(), rule.getRuleCode())) throw new ServiceException("规则编码不可变更");
        validate(rule);
        unique(rule);
        int changed = mapper.updateSysAutoCodeRule(rule);
        if (changed > 0) cache.invalidateAfterCommit();
        return changed;
    }

    @Transactional(transactionManager = "masterTransactionManager", rollbackFor = Exception.class)
    public int deleteById(Long id) {
        return deleteByIds(new Long[]{id});
    }

    @Transactional(transactionManager = "masterTransactionManager", rollbackFor = Exception.class)
    public int deleteByIds(Long[] ids) {
        if (ids == null || ids.length == 0 || ids.length > 100) throw new ServiceException("请选择编码规则");
        int count = 0;
        for (Long id : Arrays.stream(ids).distinct().sorted().toList()) {
            findById(id);
            extra.lockRule(id);
            if (extra.partCount(id) > 0 || extra.resultCount(id) > 0)
                throw new ServiceException("存在规则组成或生成记录，不能删除规则");
            count += mapper.deleteById(id);
        }
        if (count > 0) cache.invalidateAfterCommit();
        return count;
    }

    private void unique(SysAutoCodeRule rule) {
        if (UserConstants.NOT_UNIQUE.equals(checkRuleCodeUnique(rule)) || UserConstants.NOT_UNIQUE.equals(checkRuleNameUnique(rule)))
            throw new ServiceException("规则编码或名称重复");
    }

    private void validate(SysAutoCodeRule r) {
        if (r.getRuleCode() == null || !r.getRuleCode()
                .matches("[A-Za-z][A-Za-z0-9_-]{0,63}") || r.getRuleName() == null || r.getRuleName()
                .isBlank() || r.getRuleName().length() > 255 || r.getRuleDesc() != null && r.getRuleDesc()
                .length() > 500 || r.getRemark() != null && r.getRemark().length() > 500)
            throw new ServiceException("规则编码、名称或描述不符合要求");
        if (r.getMaxLength() == null || r.getMaxLength() < 1 || r.getMaxLength() > 64 || (r.getIsPadded() == null || !Set.of("Y", "N")
                .contains(r.getIsPadded())) || (r.getEnableFlag() == null || !Set.of("Y", "N")
                .contains(r.getEnableFlag()))) throw new ServiceException("长度或启用状态不符合要求");
        if ("Y".equals(r.getIsPadded())) {
            if (r.getPaddedChar() == null || r.getPaddedChar()
                    .length() != 1 || (r.getPaddedMethod() == null || !Set.of("L", "R").contains(r.getPaddedMethod())))
                throw new ServiceException("补齐字符必须为一个字符，方式为左或右");
        } else {
            r.setPaddedChar(null);
            r.setPaddedMethod(null);
        }
    }
}
