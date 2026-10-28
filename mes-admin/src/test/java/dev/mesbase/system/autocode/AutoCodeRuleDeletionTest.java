package dev.mesbase.system.autocode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;

import dev.mesbase.common.core.domain.entity.SysAutoCodeRule;
import dev.mesbase.common.core.domain.entity.SysAutoCodePart;
import dev.mesbase.common.exception.ServiceException;
import dev.mesbase.system.mapper.AutoCodeExtraMapper;
import dev.mesbase.system.mapper.SysAutoCodePartMapper;
import dev.mesbase.system.mapper.SysAutoCodeRuleMapper;
import dev.mesbase.system.service.AutoCodeRuleReferenceGuard;
import dev.mesbase.system.service.impl.AutoCodeRuleSnapshotCache;
import dev.mesbase.system.service.impl.SysAutoCodeRuleServiceImpl;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.SimpleTransactionStatus;

class AutoCodeRuleDeletionTest {
    @Test
    void ruleUpdateUsesTheSamePrefixReferenceGuardAsDeletion() {
        var rules = mock(SysAutoCodeRuleMapper.class);
        var extra = mock(AutoCodeExtraMapper.class);
        var cache = mock(AutoCodeRuleSnapshotCache.class);
        var references = mock(AutoCodeRuleReferenceGuard.class);
        var parts = mock(SysAutoCodePartMapper.class);
        var master = mock(PlatformTransactionManager.class);
        var existing = rule();
        var update = rule();
        update.setRuleName("工单规则");
        update.setMaxLength(64);
        update.setIsPadded("N");
        update.setEnableFlag("Y");
        when(rules.findById(7L)).thenReturn(existing);
        when(rules.findByIdForUpdate(7L)).thenReturn(existing);
        when(extra.lockRule(7L)).thenReturn(7L);
        when(references.requireUnreferenced(existing)).thenReturn("WO");
        when(parts.selectByRuleIdForUpdate(7L)).thenReturn(List.of(prefixPart("WO")));
        when(master.getTransaction(any(TransactionDefinition.class))).thenReturn(new SimpleTransactionStatus());
        when(rules.updateSysAutoCodeRule(update)).thenReturn(1);
        var service = new SysAutoCodeRuleServiceImpl(rules, extra, cache, references, parts, master);

        assertEquals(1, service.updateInfo(update));

        var order = inOrder(references, master);
        order.verify(references).requireUnreferenced(existing);
        order.verify(master).getTransaction(any(TransactionDefinition.class));
        verify(cache).invalidateAfterCommit();
    }

    @Test
    void ruleUpdateIsRejectedOnlyWhenItsPrefixIsReferenced() {
        var rules = mock(SysAutoCodeRuleMapper.class);
        var extra = mock(AutoCodeExtraMapper.class);
        var cache = mock(AutoCodeRuleSnapshotCache.class);
        var references = mock(AutoCodeRuleReferenceGuard.class);
        var parts = mock(SysAutoCodePartMapper.class);
        var master = mock(PlatformTransactionManager.class);
        var existing = rule();
        var update = rule();
        when(rules.findById(7L)).thenReturn(existing);
        doThrow(new ServiceException("编码规则前缀已被单据引用，不能删除或修改"))
                .when(references).requireUnreferenced(existing);
        var service = new SysAutoCodeRuleServiceImpl(rules, extra, cache, references, parts, master);

        assertThrows(ServiceException.class, () -> service.updateInfo(update));

        verifyNoInteractions(master, extra, parts, cache);
        verify(rules, never()).updateSysAutoCodeRule(any(SysAutoCodeRule.class));
    }

    @Test
    void businessReferenceScanRunsBeforeTheShortCenterTransaction() {
        var rules = mock(SysAutoCodeRuleMapper.class);
        var extra = mock(AutoCodeExtraMapper.class);
        var cache = mock(AutoCodeRuleSnapshotCache.class);
        var references = mock(AutoCodeRuleReferenceGuard.class);
        var parts = mock(SysAutoCodePartMapper.class);
        var master = mock(PlatformTransactionManager.class);
        var rule = rule();
        var secondRule = rule();
        secondRule.setRuleId(8L);
        secondRule.setRuleCode("STOCK_IN");
        secondRule.setRuleType("STOCK_IN");
        when(rules.findById(7L)).thenReturn(rule);
        when(rules.findById(8L)).thenReturn(secondRule);
        when(rules.findByIdForUpdate(7L)).thenReturn(rule);
        when(rules.findByIdForUpdate(8L)).thenReturn(secondRule);
        when(extra.lockRule(7L)).thenReturn(7L);
        when(extra.lockRule(8L)).thenReturn(8L);
        when(extra.resultCount(7L)).thenReturn(0);
        when(extra.resultCount(8L)).thenReturn(0);
        when(references.requireUnreferenced(rule)).thenReturn("WO");
        when(references.requireUnreferenced(secondRule)).thenReturn("ST");
        when(parts.selectByRuleIdForUpdate(7L)).thenReturn(List.of(prefixPart("WO")));
        when(parts.selectByRuleIdForUpdate(8L)).thenReturn(List.of(prefixPart("ST")));
        when(parts.deleteByRuleId(7L)).thenReturn(1);
        when(parts.deleteByRuleId(8L)).thenReturn(1);
        when(master.getTransaction(any(TransactionDefinition.class)))
                .thenReturn(new SimpleTransactionStatus());
        when(rules.deleteById(7L)).thenReturn(1);
        when(rules.deleteById(8L)).thenReturn(1);
        var service = new SysAutoCodeRuleServiceImpl(rules, extra, cache, references, parts, master);

        assertEquals(2, service.deleteByIds(new Long[]{8L, 7L}));

        var order = inOrder(references, master);
        order.verify(references).requireUnreferenced(rule);
        order.verify(references).requireUnreferenced(secondRule);
        order.verify(master).getTransaction(any(TransactionDefinition.class));
        verify(cache).invalidateAfterCommit();
        verify(parts).deleteByRuleId(7L);
        verify(parts).deleteByRuleId(8L);
    }

    @Test
    void referenceFailurePreventsStartingTheCenterDeleteTransaction() {
        var rules = mock(SysAutoCodeRuleMapper.class);
        var extra = mock(AutoCodeExtraMapper.class);
        var cache = mock(AutoCodeRuleSnapshotCache.class);
        var references = mock(AutoCodeRuleReferenceGuard.class);
        var parts = mock(SysAutoCodePartMapper.class);
        var master = mock(PlatformTransactionManager.class);
        var rule = rule();
        var secondRule = rule();
        secondRule.setRuleId(8L);
        when(rules.findById(7L)).thenReturn(rule);
        when(rules.findById(8L)).thenReturn(secondRule);
        doThrow(new ServiceException("单据已引用")).when(references).requireUnreferenced(secondRule);
        var service = new SysAutoCodeRuleServiceImpl(rules, extra, cache, references, parts, master);

        assertThrows(ServiceException.class, () -> service.deleteByIds(new Long[]{7L, 8L}));

        verifyNoInteractions(master, extra, cache);
        verify(rules, never()).deleteById(7L);
    }

    @Test
    void generatedCounterProtectsACategorizedRuleWhenItsPrefixWasRemoved() {
        var rules = mock(SysAutoCodeRuleMapper.class);
        var extra = mock(AutoCodeExtraMapper.class);
        var cache = mock(AutoCodeRuleSnapshotCache.class);
        var references = mock(AutoCodeRuleReferenceGuard.class);
        var parts = mock(SysAutoCodePartMapper.class);
        var master = mock(PlatformTransactionManager.class);
        var rule = rule();
        when(rules.findById(7L)).thenReturn(rule);
        when(rules.findByIdForUpdate(7L)).thenReturn(rule);
        when(references.requireUnreferenced(rule)).thenReturn("");
        when(extra.lockRule(7L)).thenReturn(7L);
        when(extra.resultCount(7L)).thenReturn(1);
        when(parts.selectByRuleIdForUpdate(7L)).thenReturn(List.of());
        when(master.getTransaction(any(TransactionDefinition.class))).thenReturn(new SimpleTransactionStatus());
        var service = new SysAutoCodeRuleServiceImpl(rules, extra, cache, references, parts, master);

        assertThrows(ServiceException.class, () -> service.deleteByIds(new Long[]{7L}));

        verify(rules, never()).deleteById(7L);
        verifyNoInteractions(cache);
    }

    private static SysAutoCodeRule rule() {
        var rule = new SysAutoCodeRule();
        rule.setRuleId(7L);
        rule.setRuleCode("WORK_ORDER");
        rule.setRuleType("WORK_ORDER");
        return rule;
    }

    private static SysAutoCodePart prefixPart(String prefix) {
        var part = new SysAutoCodePart();
        part.setPartType("FIXCHAR");
        part.setPartIndex(1);
        part.setPartLength(prefix.length());
        part.setFixCharacter(prefix);
        return part;
    }
}
