package dev.mesbase.system.service.impl;

import dev.mesbase.common.constant.UserConstants;
import dev.mesbase.common.core.domain.entity.SysAutoCodeRule;
import dev.mesbase.common.exception.ServiceException;
import dev.mesbase.system.mapper.AutoCodeExtraMapper;
import dev.mesbase.system.mapper.SysAutoCodeRuleMapper;
import dev.mesbase.system.service.IAutoCodeRuleService;
import dev.mesbase.system.service.AutoCodeRuleReferenceGuard;
import dev.mesbase.system.service.AutoCodeRuleType;
import dev.mesbase.common.core.domain.entity.SysAutoCodePart;
import dev.mesbase.system.mapper.SysAutoCodePartMapper;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.annotation.Propagation;
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
    private final AutoCodeRuleReferenceGuard references;
    private final SysAutoCodePartMapper parts;
    private final TransactionTemplate masterTransactions;

    public SysAutoCodeRuleServiceImpl(SysAutoCodeRuleMapper mapper, AutoCodeExtraMapper extra,
                                     AutoCodeRuleSnapshotCache cache, AutoCodeRuleReferenceGuard references,
                                     SysAutoCodePartMapper parts,
                                     @Qualifier("masterTransactionManager") PlatformTransactionManager masterManager) {
        this.mapper = mapper;
        this.extra = extra;
        this.cache = cache;
        this.references = references;
        this.parts = parts;
        this.masterTransactions = new TransactionTemplate(masterManager);
        this.masterTransactions.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
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
        references.requireEnabledFactory(rule.getFactoryId());
        unique(rule);
        rule.setRuleId(null);
        int changed = mapper.add(rule);
        if (changed > 0) cache.invalidateAfterCommit();
        return changed;
    }

    @Transactional(transactionManager = "masterTransactionManager", propagation = Propagation.NOT_SUPPORTED,
            rollbackFor = Exception.class)
    public int updateInfo(SysAutoCodeRule rule) {
        var scanned = findById(rule.getRuleId());
        String scannedPrefix = references.requireUnreferenced(scanned);
        if (!Objects.equals(scanned.getFactoryId(), rule.getFactoryId())) {
            if (extra.resultCount(scanned.getRuleId()) > 0)
                throw new ServiceException("规则已生成单据号，不能变更适用工厂");
            references.requireEnabledFactory(rule.getFactoryId());
            SysAutoCodeRule targetScope = new SysAutoCodeRule();
            targetScope.setRuleId(scanned.getRuleId());
            targetScope.setRuleCode(scanned.getRuleCode());
            targetScope.setRuleType(scanned.getRuleType());
            targetScope.setFactoryId(rule.getFactoryId());
            references.requireUnreferenced(targetScope);
        }
        Integer updatedCount = masterTransactions.execute(status -> {
            if (extra.lockRule(scanned.getRuleId()) == null)
                throw new ServiceException("编码规则不存在");
            var current = mapper.findByIdForUpdate(scanned.getRuleId());
            if (current == null) throw new ServiceException("编码规则不存在");
            if (!Objects.equals(scanned.getRuleCode(), current.getRuleCode())
                    || !Objects.equals(scanned.getRuleType(), current.getRuleType())
                    || !Objects.equals(scanned.getFactoryId(), current.getFactoryId()))
                throw new ServiceException("规则配置已变化，请重新执行修改");
            if (!Objects.equals(current.getFactoryId(), rule.getFactoryId())
                    && extra.resultCount(current.getRuleId()) > 0)
                throw new ServiceException("规则已生成单据号，不能变更适用工厂");
            var currentParts = parts.selectByRuleIdForUpdate(scanned.getRuleId());
            if (!Objects.equals(scannedPrefix, prefix(currentParts)))
                throw new ServiceException("规则前缀已变化，请重新执行修改");
            if (current.getRuleType() != null && !Objects.equals(current.getRuleType(), rule.getRuleType()))
                throw new ServiceException("规则单据类型不可变更");
            validate(rule);
            unique(rule);
            int changed = mapper.updateSysAutoCodeRule(rule);
            if (changed > 0) cache.invalidateAfterCommit();
            return changed;
        });
        if (updatedCount == null) throw new ServiceException("更新编码规则失败");
        return updatedCount;
    }

    @Transactional(transactionManager = "masterTransactionManager", propagation = Propagation.NOT_SUPPORTED,
            rollbackFor = Exception.class)
    public int deleteById(Long id) {
        return deleteByIds(new Long[]{id});
    }

    @Transactional(transactionManager = "masterTransactionManager", propagation = Propagation.NOT_SUPPORTED,
            rollbackFor = Exception.class)
    public int deleteByIds(Long[] ids) {
        if (ids == null || ids.length == 0 || ids.length > 100) throw new ServiceException("请选择编码规则");
        var scanned = Arrays.stream(ids).distinct().sorted().map(id -> {
            var rule = findById(id);
            return new DeletionCandidate(rule, references.requireUnreferenced(rule));
        }).toList();
        return masterTransactions.execute(status -> deleteBatch(scanned));
    }

    private record DeletionCandidate(SysAutoCodeRule rule, String prefix) { }

    private int deleteBatch(List<DeletionCandidate> scannedRules) {
        var currentRules = new ArrayList<SysAutoCodeRule>(scannedRules.size());
        var currentParts = new ArrayList<List<SysAutoCodePart>>(scannedRules.size());
        for (DeletionCandidate candidate : scannedRules) {
            SysAutoCodeRule scanned = candidate.rule();
            extra.lockRule(scanned.getRuleId());
            var current = mapper.findByIdForUpdate(scanned.getRuleId());
            if (current == null) throw new ServiceException("编码规则不存在");
            if (!Objects.equals(scanned.getRuleCode(), current.getRuleCode())
                    || !Objects.equals(scanned.getRuleType(), current.getRuleType()))
                throw new ServiceException("规则配置已变化，请重新执行删除");
            var configuredParts = parts.selectByRuleIdForUpdate(scanned.getRuleId());
            if (!Objects.equals(candidate.prefix(), prefix(configuredParts)))
                throw new ServiceException("规则前缀已变化，请重新执行删除");
            if (extra.resultCount(scanned.getRuleId()) > 0)
                throw new ServiceException("存在生成记录，不能删除规则");
            currentRules.add(current);
            currentParts.add(configuredParts);
        }
        int count = 0;
        for (int index = 0; index < currentRules.size(); index++) {
            SysAutoCodeRule current = currentRules.get(index);
            int expectedParts = currentParts.get(index).size();
            if (expectedParts > 0 && parts.deleteByRuleId(current.getRuleId()) != expectedParts)
                throw new ServiceException("规则组成已变化，请重新执行删除");
            count += mapper.deleteById(current.getRuleId());
        }
        if (count > 0) cache.invalidateAfterCommit();
        return count;
    }

    private static String prefix(List<SysAutoCodePart> configuredParts) {
        var value = new StringBuilder();
        for (SysAutoCodePart part : configuredParts) {
            if (!"FIXCHAR".equals(part.getPartType())) break;
            if (part.getFixCharacter() != null) value.append(part.getFixCharacter());
        }
        return value.toString();
    }

    private void unique(SysAutoCodeRule rule) {
        if (UserConstants.NOT_UNIQUE.equals(checkRuleCodeUnique(rule)) || UserConstants.NOT_UNIQUE.equals(checkRuleNameUnique(rule)))
            throw new ServiceException("规则编码或名称重复");
    }

    private void validate(SysAutoCodeRule r) {
        if (r.getFactoryId() != null && r.getFactoryId().isBlank()) r.setFactoryId(null);
        if (r.getRuleCode() == null || !r.getRuleCode()
                .matches("[A-Za-z0-9][A-Za-z0-9_-]{0,63}") || r.getRuleName() == null || r.getRuleName()
                .isBlank() || r.getRuleName().length() > 255 || r.getRuleDesc() != null && r.getRuleDesc()
                .length() > 500 || r.getRemark() != null && r.getRemark().length() > 500
                || r.getFactoryId() != null && !r.getFactoryId().matches("[A-Za-z0-9_-]{1,64}"))
            throw new ServiceException("规则编码、名称或描述不符合要求");
        if (!AutoCodeRuleType.supports(r.getRuleType())) throw new ServiceException("请选择有效的单据编码类型");
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
