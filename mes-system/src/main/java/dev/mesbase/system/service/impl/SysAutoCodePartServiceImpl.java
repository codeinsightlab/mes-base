package dev.mesbase.system.service.impl;

import dev.mesbase.common.constant.UserConstants;
import dev.mesbase.common.core.domain.entity.SysAutoCodePart;
import dev.mesbase.common.core.domain.entity.SysAutoCodeRule;
import dev.mesbase.common.exception.ServiceException;
import dev.mesbase.system.mapper.AutoCodeExtraMapper;
import dev.mesbase.system.mapper.SysAutoCodePartMapper;
import dev.mesbase.system.mapper.SysAutoCodeRuleMapper;
import dev.mesbase.system.service.IAutoCodePartService;
import dev.mesbase.system.service.AutoCodeRuleReferenceGuard;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Objects;
import java.util.Set;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Source SysAutoCodePartServiceImpl PORT; rule ownership and serialized edits.
 */
@Service
@ConditionalOnProperty(name = "mes.system.enabled", havingValue = "true")
public class SysAutoCodePartServiceImpl implements IAutoCodePartService {
    private final SysAutoCodePartMapper mapper;
    private final SysAutoCodeRuleMapper rules;
    private final AutoCodeExtraMapper extra;
    private final AutoCodeRuleSnapshotCache cache;
    private final AutoCodeRuleReferenceGuard references;
    private final TransactionTemplate masterTransactions;

    public SysAutoCodePartServiceImpl(SysAutoCodePartMapper mapper, SysAutoCodeRuleMapper rules,
                                      AutoCodeExtraMapper extra, AutoCodeRuleSnapshotCache cache,
                                      AutoCodeRuleReferenceGuard references,
                                      @Qualifier("masterTransactionManager") PlatformTransactionManager masterManager) {
        this.mapper = mapper;
        this.rules = rules;
        this.extra = extra;
        this.cache = cache;
        this.references = references;
        this.masterTransactions = new TransactionTemplate(masterManager);
        this.masterTransactions.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
    }

    public List<SysAutoCodePart> listPart(SysAutoCodePart query) {
        return mapper.selectSysAutoCodePartList(query);
    }

    public SysAutoCodePart findById(Long id) {
        var p = mapper.findById(id);
        if (p == null) throw new ServiceException("规则组成不存在");
        return p;
    }

    public String checkPartUnique(SysAutoCodePart p) {
        return mapper.checkPartUnique(p) == null ? UserConstants.UNIQUE : UserConstants.NOT_UNIQUE;
    }

    @Transactional(transactionManager = "masterTransactionManager", propagation = Propagation.NOT_SUPPORTED,
            rollbackFor = Exception.class)
    public int insertPart(SysAutoCodePart p) {
        p.setPartId(null);
        validate(p);
        List<SysAutoCodePart> before = configuredParts(p.getRuleId());
        String oldPrefix = prefix(before);
        List<SysAutoCodePart> after = new ArrayList<>(before);
        after.add(p);
        String newPrefix = prefix(after);
        checkPrefixChange(p.getRuleId(), oldPrefix, newPrefix);
        return masterTransactions.execute(status -> {
            lockRule(p.getRuleId());
            List<SysAutoCodePart> current = partsForUpdate(p.getRuleId());
            requireSamePrefix(oldPrefix, current);
            List<SysAutoCodePart> currentAfter = new ArrayList<>(current);
            currentAfter.add(p);
            requireSamePrefix(newPrefix, currentAfter);
            requireNoGeneratedNumbers(p.getRuleId(), oldPrefix, newPrefix);
            prepare(p);
            int changed = mapper.add(p);
            if (changed > 0) cache.invalidateAfterCommit();
            return changed;
        });
    }

    @Transactional(transactionManager = "masterTransactionManager", propagation = Propagation.NOT_SUPPORTED,
            rollbackFor = Exception.class)
    public int updatePart(SysAutoCodePart p) {
        var old = findById(p.getPartId());
        if (!Objects.equals(old.getRuleId(), p.getRuleId())) throw new ServiceException("不能变更所属规则");
        validate(p);
        List<SysAutoCodePart> before = configuredParts(old.getRuleId());
        String oldPrefix = prefix(before);
        List<SysAutoCodePart> after = replace(before, p);
        String newPrefix = prefix(after);
        checkPrefixChange(old.getRuleId(), oldPrefix, newPrefix);
        return masterTransactions.execute(status -> {
            lockRule(old.getRuleId());
            List<SysAutoCodePart> current = partsForUpdate(old.getRuleId());
            requireSamePrefix(oldPrefix, current);
            SysAutoCodePart lockedOld = current.stream()
                    .filter(part -> Objects.equals(part.getPartId(), p.getPartId())).findFirst()
                    .orElseThrow(() -> new ServiceException("规则组成已变化，请重新加载"));
            if (!samePart(lockedOld, old)) throw new ServiceException("规则组成已变化，请重新加载");
            requireSamePrefix(newPrefix, replace(current, p));
            requireNoGeneratedNumbers(old.getRuleId(), oldPrefix, newPrefix);
            prepare(p);
            int changed = mapper.updateSysAutoCodePart(p);
            if (changed > 0) cache.invalidateAfterCommit();
            return changed;
        });
    }

    @Transactional(transactionManager = "masterTransactionManager", propagation = Propagation.NOT_SUPPORTED,
            rollbackFor = Exception.class)
    public int deleteByIds(Long[] ids) {
        if (ids == null || ids.length == 0 || ids.length > 100) throw new ServiceException("请选择规则组成");
        var partIds = Arrays.stream(ids).distinct().sorted().toList();
        var rows = partIds.stream().map(this::findById).toList();
        var ruleIds = rows.stream().map(SysAutoCodePart::getRuleId).distinct().sorted().toList();
        var scannedPrefixes = new LinkedHashMap<Long, String>();
        var scannedNewPrefixes = new LinkedHashMap<Long, String>();
        for (Long ruleId : ruleIds) {
            List<SysAutoCodePart> before = configuredParts(ruleId);
            String oldPrefix = prefix(before);
            List<SysAutoCodePart> after = before.stream()
                    .filter(part -> !partIds.contains(part.getPartId())).toList();
            String newPrefix = prefix(after);
            checkPrefixChange(ruleId, oldPrefix, newPrefix);
            scannedPrefixes.put(ruleId, oldPrefix);
            scannedNewPrefixes.put(ruleId, newPrefix);
        }
        return masterTransactions.execute(status -> {
            for (Long ruleId : ruleIds) lockRule(ruleId);
            for (Long ruleId : ruleIds) {
                List<SysAutoCodePart> current = partsForUpdate(ruleId);
                requireSamePrefix(scannedPrefixes.get(ruleId), current);
                List<SysAutoCodePart> currentAfter = current.stream()
                        .filter(part -> !partIds.contains(part.getPartId())).toList();
                requireSamePrefix(scannedNewPrefixes.get(ruleId), currentAfter);
                requireNoGeneratedNumbers(ruleId, scannedPrefixes.get(ruleId), scannedNewPrefixes.get(ruleId));
            }
            int count = partIds.stream().mapToInt(mapper::deleteById).sum();
            if (count != partIds.size()) throw new ServiceException("规则组成已变化，请重新执行删除");
            if (count > 0) cache.invalidateAfterCommit();
            return count;
        });
    }

    private void prepare(SysAutoCodePart p) {
        if (p.getRuleId() == null)
            throw new ServiceException("所属规则不存在");
        if (extra.lockRule(p.getRuleId()) == null)
            throw new ServiceException("所属规则不存在");
        if (rules.findByIdForUpdate(p.getRuleId()) == null)
            throw new ServiceException("所属规则不存在");
        validate(p);
        if (UserConstants.NOT_UNIQUE.equals(checkPartUnique(p))) throw new ServiceException("组成编码、名称或顺序重复");
        if ("SERIALNO".equals(p.getPartType())) {
            if (mapper.selectByRuleIdForUpdate(p.getRuleId()).stream()
                    .filter(other -> "SERIALNO".equals(other.getPartType()))
                    .anyMatch(other -> !Objects.equals(other.getPartId(), p.getPartId())))
                throw new ServiceException("一个规则只能有一个流水号组成");
        }
        p.setSeriaNowNo(null);
    }

    private void checkPrefixChange(Long ruleId, String oldPrefix, String newPrefix) {
        if (Objects.equals(oldPrefix, newPrefix)) return;
        SysAutoCodeRule rule = rules.findById(ruleId);
        if (rule == null) throw new ServiceException("所属规则不存在");
        references.requirePrefixChangeUnreferenced(rule, oldPrefix, newPrefix);
    }

    private List<SysAutoCodePart> configuredParts(Long ruleId) {
        if (ruleId == null) throw new ServiceException("所属规则不存在");
        SysAutoCodePart query = new SysAutoCodePart();
        query.setRuleId(ruleId);
        return mapper.selectSysAutoCodePartList(query).stream()
                .sorted(Comparator.comparing(SysAutoCodePart::getPartIndex)).toList();
    }

    private List<SysAutoCodePart> partsForUpdate(Long ruleId) {
        return mapper.selectByRuleIdForUpdate(ruleId).stream()
                .sorted(Comparator.comparing(SysAutoCodePart::getPartIndex)).toList();
    }

    private void lockRule(Long ruleId) {
        if (extra.lockRule(ruleId) == null || rules.findByIdForUpdate(ruleId) == null)
            throw new ServiceException("所属规则不存在");
    }

    private void requireSamePrefix(String scannedPrefix, List<SysAutoCodePart> current) {
        if (!Objects.equals(scannedPrefix, prefix(current)))
            throw new ServiceException("规则组成已变化，请重新执行操作");
    }

    private void requireNoGeneratedNumbers(Long ruleId, String oldPrefix, String newPrefix) {
        if (!Objects.equals(oldPrefix, newPrefix) && extra.resultCount(ruleId) > 0)
            throw new ServiceException("已生成编号的规则不能修改前缀");
    }

    private static List<SysAutoCodePart> replace(List<SysAutoCodePart> before, SysAutoCodePart replacement) {
        var result = new ArrayList<SysAutoCodePart>(before.size());
        boolean found = false;
        for (SysAutoCodePart part : before) {
            if (Objects.equals(part.getPartId(), replacement.getPartId())) {
                result.add(replacement);
                found = true;
            } else result.add(part);
        }
        if (!found) throw new ServiceException("规则组成不存在");
        return result;
    }

    private static String prefix(List<SysAutoCodePart> parts) {
        var result = new StringBuilder();
        for (SysAutoCodePart part : parts.stream().sorted(Comparator.comparing(SysAutoCodePart::getPartIndex)).toList()) {
            if (!"FIXCHAR".equals(part.getPartType())) break;
            if (part.getFixCharacter() != null) result.append(part.getFixCharacter());
        }
        return result.toString();
    }

    private static boolean samePart(SysAutoCodePart left, SysAutoCodePart right) {
        return Objects.equals(left.getRuleId(), right.getRuleId())
                && Objects.equals(left.getPartCode(), right.getPartCode())
                && Objects.equals(left.getPartName(), right.getPartName())
                && Objects.equals(left.getPartType(), right.getPartType())
                && Objects.equals(left.getPartIndex(), right.getPartIndex())
                && Objects.equals(left.getPartLength(), right.getPartLength())
                && Objects.equals(left.getFixCharacter(), right.getFixCharacter())
                && Objects.equals(left.getDateFormat(), right.getDateFormat())
                && Objects.equals(left.getSeriaStartNo(), right.getSeriaStartNo())
                && Objects.equals(left.getSeriaStep(), right.getSeriaStep())
                && Objects.equals(left.getCycleFlag(), right.getCycleFlag())
                && Objects.equals(left.getCycleMethod(), right.getCycleMethod());
    }

    private void validate(SysAutoCodePart p) {
        if (p.getPartCode() == null || !p.getPartCode()
                .matches("[A-Za-z][A-Za-z0-9_-]{0,63}") || p.getPartName() == null || p.getPartName()
                .isBlank() || p.getPartName()
                .length() > 255 || p.getPartIndex() == null || p.getPartIndex() < 1 || p.getPartIndex() > 999 || p.getPartLength() == null || p.getPartLength() < 1 || p.getPartLength() > 64 || p.getPartType() == null)
            throw new ServiceException("组成编码、名称、顺序或长度不符合要求");
        switch (p.getPartType()) {
            case "INPUTCHAR" -> {
                p.setFixCharacter(null);
                p.setDateFormat(null);
            }
            case "FIXCHAR" -> {
                if (p.getFixCharacter() == null || p.getFixCharacter().length() != p.getPartLength())
                    throw new ServiceException("固定字符与组成长度不一致");
            }
            case "NOWDATE" -> {
                if (p.getDateFormat() == null || !Set.of("yyyy", "yyyyMM", "yyyyMMdd", "yyyyMMddHH", "yyyyMMddHHmm", "yyyyMMddHHmmss", "yyMMdd")
                        .contains(p.getDateFormat()) || DateTimeFormatter.ofPattern(p.getDateFormat())
                        .format(LocalDateTime.now()).length() != p.getPartLength())
                    throw new ServiceException("日期格式或组成长度不符合要求");
            }
            case "SERIALNO" -> {
                if (p.getSeriaStartNo() == null || p.getSeriaStartNo() < 0 || p.getSeriaStep() == null || p.getSeriaStep() < 1 || p.getCycleFlag() == null || !Set.of("Y", "N")
                        .contains(p.getCycleFlag()) || "Y".equals(p.getCycleFlag()) && (p.getCycleMethod() == null || !Set.of("YEAR", "MONTH", "DAY", "HOUR", "MINUTE", "OTHER")
                        .contains(p.getCycleMethod())))
                    throw new ServiceException("流水号起始、步长或循环设置不符合要求");
            }
            default -> throw new ServiceException("不支持的规则组成类型");
        }
        if (p.getRemark() != null && p.getRemark().length() > 500) throw new ServiceException("备注过长");
    }
}
