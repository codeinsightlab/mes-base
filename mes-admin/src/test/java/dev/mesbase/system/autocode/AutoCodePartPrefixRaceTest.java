package dev.mesbase.system.autocode;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.mesbase.common.core.domain.entity.SysAutoCodePart;
import dev.mesbase.common.core.domain.entity.SysAutoCodeRule;
import dev.mesbase.common.exception.ServiceException;
import dev.mesbase.system.mapper.AutoCodeExtraMapper;
import dev.mesbase.system.mapper.SysAutoCodePartMapper;
import dev.mesbase.system.mapper.SysAutoCodeRuleMapper;
import dev.mesbase.system.service.AutoCodeRuleReferenceGuard;
import dev.mesbase.system.service.impl.AutoCodeRuleSnapshotCache;
import dev.mesbase.system.service.impl.SysAutoCodePartServiceImpl;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.SimpleTransactionStatus;

class AutoCodePartPrefixRaceTest {
    @Test
    void insertionRechecksTheProposedPrefixAfterLockingTheCurrentComposition() {
        var parts = mock(SysAutoCodePartMapper.class);
        var rules = mock(SysAutoCodeRuleMapper.class);
        var extra = mock(AutoCodeExtraMapper.class);
        var cache = mock(AutoCodeRuleSnapshotCache.class);
        var references = mock(AutoCodeRuleReferenceGuard.class);
        var transactions = mock(PlatformTransactionManager.class);
        var rule = new SysAutoCodeRule();
        rule.setRuleId(7L);
        rule.setRuleType("WORK_ORDER");
        var before = List.of(prefix(1, "A", 1), date(2, 3));
        var current = List.of(prefix(1, "A", 1), date(2, 5));
        var insert = prefix(null, "B", 4);
        insert.setRuleId(7L);
        insert.setPartCode("PREFIX_B");
        insert.setPartName("前缀B");
        when(parts.selectSysAutoCodePartList(any(SysAutoCodePart.class))).thenReturn(before);
        when(extra.lockRule(7L)).thenReturn(7L);
        when(rules.findByIdForUpdate(7L)).thenReturn(rule);
        when(parts.selectByRuleIdForUpdate(7L)).thenReturn(current);
        when(transactions.getTransaction(any(TransactionDefinition.class))).thenReturn(new SimpleTransactionStatus());
        var service = new SysAutoCodePartServiceImpl(parts, rules, extra, cache, references, transactions);

        assertThrows(ServiceException.class, () -> service.insertPart(insert));

        verify(parts, never()).add(any(SysAutoCodePart.class));
        verify(references, never()).requirePrefixChangeUnreferenced(any(), any(), any());
    }

    private static SysAutoCodePart prefix(Integer id, String value, int index) {
        var part = new SysAutoCodePart();
        if (id != null) part.setPartId(id.longValue());
        part.setRuleId(7L);
        part.setPartCode("PREFIX_" + value);
        part.setPartName("前缀" + value);
        part.setPartType("FIXCHAR");
        part.setPartIndex(index);
        part.setPartLength(value.length());
        part.setFixCharacter(value);
        return part;
    }

    private static SysAutoCodePart date(int id, int index) {
        var part = new SysAutoCodePart();
        part.setPartId((long) id);
        part.setRuleId(7L);
        part.setPartCode("DATE");
        part.setPartName("日期");
        part.setPartType("NOWDATE");
        part.setPartIndex(index);
        part.setPartLength(8);
        part.setDateFormat("yyyyMMdd");
        return part;
    }
}
