package dev.mesbase.system.autocode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.mesbase.common.core.domain.entity.SysAutoCodePart;
import dev.mesbase.common.core.domain.entity.SysAutoCodeResult;
import dev.mesbase.common.core.domain.entity.SysAutoCodeRule;
import dev.mesbase.system.mapper.AutoCodeExtraMapper;
import dev.mesbase.system.mapper.SysAutoCodePartMapper;
import dev.mesbase.system.mapper.SysAutoCodeRuleMapper;
import dev.mesbase.system.mapper.SysAutoCodeResultMapper;
import dev.mesbase.system.service.impl.AutoCodeRuleSnapshotCache;
import dev.mesbase.system.service.impl.SysAutoCodeGenerationServiceImpl;
import dev.mesbase.system.strategy.PartTypeFixCharHandler;
import dev.mesbase.system.strategy.PartTypeHandler;
import dev.mesbase.system.strategy.PartTypeInputCharHandler;
import dev.mesbase.system.strategy.PartTypeNowDateHandler;
import dev.mesbase.system.strategy.PartTypeSerialNoHandler;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.SimpleTransactionStatus;

class AutoCodeGenerationTest {
    @Test
    void formalCallsKeepIndependentDailyCountersAndGrowPastMinimumWidth() {
        var cache = mock(AutoCodeRuleSnapshotCache.class);
        var rules = mock(SysAutoCodeRuleMapper.class);
        var parts = mock(SysAutoCodePartMapper.class);
        var results = mock(SysAutoCodeResultMapper.class);
        var extra = mock(AutoCodeExtraMapper.class);
        var manager = mock(PlatformTransactionManager.class);
        var counters = new HashMap<String, SysAutoCodeResult>();
        when(manager.getTransaction(any(TransactionDefinition.class))).thenReturn(new SimpleTransactionStatus());
        when(extra.lockRule(anyLong())).thenAnswer(invocation -> invocation.getArgument(0));
        when(extra.counter(anyLong(), any(String.class))).thenAnswer(invocation ->
                counters.get(invocation.getArgument(0) + ":" + invocation.getArgument(1)));
        doAnswer(invocation -> {
            SysAutoCodeResult row = invocation.getArgument(0);
            counters.put(row.getRuleId() + ":" + row.getCycleKey(), row);
            return null;
        }).when(results).add(any(SysAutoCodeResult.class));
        doAnswer(invocation -> {
            SysAutoCodeResult row = invocation.getArgument(0);
            counters.put(row.getRuleId() + ":" + row.getCycleKey(), row);
            return null;
        }).when(results).updateAutoCodeResult(any(SysAutoCodeResult.class));
        when(cache.get("SALES_ORDER")).thenReturn(config(1, "SALES_ORDER", "SO"));
        when(cache.get("STOCK_RECEIPT")).thenReturn(config(2, "STOCK_RECEIPT", "RK"));
        when(rules.findByIdForUpdate(anyLong())).thenAnswer(invocation -> rule(invocation.getArgument(0)));
        when(parts.selectByRuleIdForUpdate(anyLong())).thenAnswer(invocation -> {
            long id = invocation.getArgument(0);
            return entities(id == 1 ? "SO" : "RK", id);
        });
        var handler = new PartTypeHandler(new PartTypeInputCharHandler(), new PartTypeNowDateHandler(),
                new PartTypeFixCharHandler(), new PartTypeSerialNoHandler());
        var service = new SysAutoCodeGenerationServiceImpl(cache, rules, parts, results, extra, handler, manager);
        String date = LocalDate.now(ZoneId.of("Asia/Shanghai")).format(DateTimeFormatter.BASIC_ISO_DATE);

        assertEquals("SO" + date + "001", service.getDocNo("SALES_ORDER"));
        assertEquals("RK" + date + "001", service.getDocNo("STOCK_RECEIPT"));
        assertEquals("SO" + date + "002", service.getDocNo("SALES_ORDER"));
        seed(counters, 1, "DAY:" + date, 998);
        assertEquals("SO" + date + "999", service.getDocNo("SALES_ORDER"));
        assertEquals("SO" + date + "1000", service.getDocNo("SALES_ORDER"));
        assertEquals("SO" + date + "1001", service.getDocNo("SALES_ORDER"));
        assertTrue(service.previewDocNo("SALES_ORDER").endsWith("1002"));
        assertEquals(1001, counters.get("1:DAY:" + date).getLastSerialNo());
    }

    private static AutoCodeRuleSnapshotCache.RuleConfig config(long id, String code, String prefix) {
        return new AutoCodeRuleSnapshotCache.RuleConfig(id, code, 64, "N", null, null, "Y", List.of(
                new AutoCodeRuleSnapshotCache.PartConfig(id * 10, id, 1, "FIXCHAR", prefix.length(), null,
                        prefix, null, null, null, null),
                new AutoCodeRuleSnapshotCache.PartConfig(id * 10 + 1, id, 2, "NOWDATE", 8, "yyyyMMdd",
                        null, null, null, null, null),
                new AutoCodeRuleSnapshotCache.PartConfig(id * 10 + 2, id, 3, "SERIALNO", 3, null,
                        null, 1, 1, "Y", "DAY")));
    }

    private static SysAutoCodeRule rule(long id) {
        var row = new SysAutoCodeRule();
        row.setRuleId(id);
        row.setRuleCode(id == 1 ? "SALES_ORDER" : "STOCK_RECEIPT");
        row.setMaxLength(64);
        row.setIsPadded("N");
        row.setEnableFlag("Y");
        return row;
    }

    private static List<SysAutoCodePart> entities(String prefix, long ruleId) {
        var fixed = new SysAutoCodePart();
        fixed.setRuleId(ruleId);
        fixed.setPartIndex(1);
        fixed.setPartType("FIXCHAR");
        fixed.setPartLength(prefix.length());
        fixed.setFixCharacter(prefix);
        var date = new SysAutoCodePart();
        date.setRuleId(ruleId);
        date.setPartIndex(2);
        date.setPartType("NOWDATE");
        date.setPartLength(8);
        date.setDateFormat("yyyyMMdd");
        var serial = new SysAutoCodePart();
        serial.setRuleId(ruleId);
        serial.setPartIndex(3);
        serial.setPartType("SERIALNO");
        serial.setPartLength(3);
        serial.setSeriaStartNo(1);
        serial.setSeriaStep(1);
        serial.setCycleFlag("Y");
        serial.setCycleMethod("DAY");
        return List.of(fixed, date, serial);
    }

    private static void seed(Map<String, SysAutoCodeResult> counters, long ruleId, String cycle, int value) {
        var row = new SysAutoCodeResult();
        row.setRuleId(ruleId);
        row.setCycleKey(cycle);
        row.setLastSerialNo(value);
        row.setGenIndex(value);
        counters.put(ruleId + ":" + cycle, row);
    }
}
