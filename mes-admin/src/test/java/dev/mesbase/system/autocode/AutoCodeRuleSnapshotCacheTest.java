package dev.mesbase.system.autocode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.mesbase.common.core.domain.entity.SysAutoCodePart;
import dev.mesbase.common.core.domain.entity.SysAutoCodeRule;
import dev.mesbase.common.core.redis.RedisCache;
import dev.mesbase.system.mapper.SysAutoCodePartMapper;
import dev.mesbase.system.mapper.SysAutoCodeRuleMapper;
import dev.mesbase.system.service.impl.AutoCodeRuleSnapshotCache;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class AutoCodeRuleSnapshotCacheTest {
    @Test
    void firstLookupLoadsAllRulesAndPartsOnceThenUsesTheWholeSnapshot() {
        var redis = mock(RedisCache.class);
        var rules = mock(SysAutoCodeRuleMapper.class);
        var parts = mock(SysAutoCodePartMapper.class);
        var stored = new AtomicReference<AutoCodeRuleSnapshotCache.Snapshot>();
        when(redis.getCacheVersion(anyString())).thenReturn(null);
        when(redis.getCacheObject(anyString())).thenAnswer(invocation -> stored.get());
        when(redis.setCacheObjectIfVersion(anyString(), nullable(String.class), anyString(),
                org.mockito.ArgumentMatchers.any(), anyInt()))
                .thenAnswer(invocation -> {
                    stored.set(invocation.getArgument(3));
                    return true;
                });
        var rule = new SysAutoCodeRule();
        rule.setRuleId(7L);
        rule.setRuleCode("SALES_ORDER");
        rule.setEnableFlag("Y");
        when(rules.selectAllForGeneration()).thenReturn(List.of(rule));
        var part = new SysAutoCodePart();
        part.setPartId(70L);
        part.setRuleId(7L);
        part.setPartIndex(1);
        part.setPartType("FIXCHAR");
        part.setPartLength(2);
        part.setFixCharacter("SO");
        when(parts.selectAllForGeneration()).thenReturn(List.of(part));
        var cache = new AutoCodeRuleSnapshotCache(redis, rules, parts);

        assertEquals("SO", cache.get("SALES_ORDER").parts().getFirst().fixCharacter());
        assertEquals(7L, cache.get("SALES_ORDER").ruleId());

        verify(rules).selectAllForGeneration();
        verify(parts).selectAllForGeneration();
    }
}
