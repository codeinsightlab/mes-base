package dev.mesbase.system.autocode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.mesbase.common.core.domain.entity.SysAutoCodePart;
import dev.mesbase.common.core.domain.entity.SysAutoCodeResult;
import dev.mesbase.common.core.domain.entity.SysAutoCodeRule;
import dev.mesbase.common.exception.ServiceException;
import dev.mesbase.system.mapper.AutoCodeExtraMapper;
import dev.mesbase.system.mapper.SysAutoCodePartMapper;
import dev.mesbase.system.mapper.SysAutoCodeRuleMapper;
import dev.mesbase.system.mapper.SysAutoCodeResultMapper;
import dev.mesbase.system.service.AutoCodeFactoryContext;
import dev.mesbase.system.service.AutoCodeNumbering;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.SimpleTransactionStatus;

class AutoCodeGenerationTest {
    @Test
    void ruleOptionsIncludeGlobalAndCurrentFactoryRulesOnly() {
        var cache = mock(AutoCodeRuleSnapshotCache.class);
        var factoryContext = mock(AutoCodeFactoryContext.class);
        when(factoryContext.requiredFactoryId()).thenReturn("FACTORY_A");
        when(cache.byType("WORK_ORDER")).thenReturn(List.of(
                config(1, "GLOBAL", "WORK_ORDER", null, "GL"),
                config(2, "LOCAL_A", "WORK_ORDER", "FACTORY_A", "A"),
                config(3, "LOCAL_B", "WORK_ORDER", "FACTORY_B", "B")));
        var service = service(cache, mock(SysAutoCodeRuleMapper.class), mock(SysAutoCodePartMapper.class),
                mock(SysAutoCodeResultMapper.class), mock(AutoCodeExtraMapper.class),
                mock(PlatformTransactionManager.class), factoryContext);

        assertIterableEquals(List.of("GLOBAL", "LOCAL_A"), service.rules("WORK_ORDER").stream()
                .map(AutoCodeNumbering.RuleOption::ruleCode).toList());
        assertEquals("LOCAL_A", service.uniqueRuleCode("WORK_ORDER"));
    }

    @Test
    void factoryRuleCannotBeGeneratedOutsideItsFactoryScope() {
        var cache = mock(AutoCodeRuleSnapshotCache.class);
        var rules = mock(SysAutoCodeRuleMapper.class);
        var factoryContext = mock(AutoCodeFactoryContext.class);
        when(factoryContext.requiredFactoryId()).thenReturn("FACTORY_A");
        when(cache.get("LOCAL_B")).thenReturn(config(3, "LOCAL_B", "WORK_ORDER", "FACTORY_B", "LB"));
        var service = service(cache, rules, mock(SysAutoCodePartMapper.class), mock(SysAutoCodeResultMapper.class),
                mock(AutoCodeExtraMapper.class), mock(PlatformTransactionManager.class), factoryContext);

        assertThrows(ServiceException.class, () -> service.getDocNo("LOCAL_B"));
        org.mockito.Mockito.verifyNoInteractions(rules);
    }

    @Test
    void numericLeadingRuleCodeCanBeGenerated() {
        var cache = mock(AutoCodeRuleSnapshotCache.class);
        var rules = mock(SysAutoCodeRuleMapper.class);
        var parts = mock(SysAutoCodePartMapper.class);
        var results = mock(SysAutoCodeResultMapper.class);
        var extra = mock(AutoCodeExtraMapper.class);
        var manager = mock(PlatformTransactionManager.class);
        var rule = rule(1L);
        rule.setRuleCode("5600-");
        when(manager.getTransaction(any(TransactionDefinition.class))).thenReturn(new SimpleTransactionStatus());
        when(extra.lockRule(1L)).thenReturn(1L);
        when(extra.counter(eq(1L), eq("FACTORY_A"), any(String.class))).thenReturn(null);
        when(cache.get("5600-")).thenReturn(config(1, "5600-", "WO"));
        when(rules.findByIdForUpdate(1L)).thenReturn(rule);
        when(parts.selectByRuleIdForUpdate(1L)).thenReturn(entities("WO", 1));
        var handler = new PartTypeHandler(new PartTypeInputCharHandler(), new PartTypeNowDateHandler(),
                new PartTypeFixCharHandler(), new PartTypeSerialNoHandler());
        var factoryContext = mock(AutoCodeFactoryContext.class);
        when(factoryContext.requiredFactoryId()).thenReturn("FACTORY_A");
        var service = new SysAutoCodeGenerationServiceImpl(cache, rules, parts, results, extra, handler,
                manager, factoryContext);
        String date = LocalDate.now(ZoneId.of("Asia/Shanghai")).format(DateTimeFormatter.BASIC_ISO_DATE);

        assertEquals("WO" + date + "001", service.getDocNo("5600-"));
    }

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
        when(extra.counter(anyLong(), any(String.class), any(String.class))).thenAnswer(invocation ->
                counters.get(invocation.getArgument(0) + ":" + invocation.getArgument(1) + ":" + invocation.getArgument(2)));
        doAnswer(invocation -> {
            SysAutoCodeResult row = invocation.getArgument(0);
            counters.put(row.getRuleId() + ":" + row.getFactoryId() + ":" + row.getCycleKey(), row);
            return null;
        }).when(results).add(any(SysAutoCodeResult.class));
        doAnswer(invocation -> {
            SysAutoCodeResult row = invocation.getArgument(0);
            counters.put(row.getRuleId() + ":" + row.getFactoryId() + ":" + row.getCycleKey(), row);
            return null;
        }).when(results).updateAutoCodeResult(any(SysAutoCodeResult.class));
        when(cache.get("SALES_ORDER")).thenReturn(config(1, "SALES_ORDER", "SO"));
        when(cache.get("STOCK_RECEIPT")).thenReturn(config(2, "STOCK_RECEIPT", "STOCK_IN", "RK"));
        when(rules.findByIdForUpdate(anyLong())).thenAnswer(invocation -> rule(invocation.getArgument(0)));
        when(parts.selectByRuleIdForUpdate(anyLong())).thenAnswer(invocation -> {
            long id = invocation.getArgument(0);
            return entities(id == 1 ? "SO" : "RK", id);
        });
        var handler = new PartTypeHandler(new PartTypeInputCharHandler(), new PartTypeNowDateHandler(),
                new PartTypeFixCharHandler(), new PartTypeSerialNoHandler());
        var factory = new AtomicReference<>("FACTORY_A");
        var factoryContext = mock(AutoCodeFactoryContext.class);
        when(factoryContext.requiredFactoryId()).thenAnswer(invocation -> factory.get());
        var service = new SysAutoCodeGenerationServiceImpl(cache, rules, parts, results, extra, handler, manager, factoryContext);
        String date = LocalDate.now(ZoneId.of("Asia/Shanghai")).format(DateTimeFormatter.BASIC_ISO_DATE);

        assertEquals("SO" + date + "001", service.getDocNo("SALES_ORDER"));
        assertEquals("RK" + date + "001", service.getDocNo("STOCK_RECEIPT"));
        assertEquals("RK" + date + "002", service.getDocNo("STOCK_RECEIPT", "STOCK_IN"));
        assertThrows(ServiceException.class, () -> service.getDocNo("STOCK_RECEIPT", "STOCK_OUT"));
        assertEquals("SO" + date + "002", service.getDocNo("SALES_ORDER"));
        factory.set("FACTORY_B");
        assertEquals("SO" + date + "001", service.getDocNo("SALES_ORDER"));
        factory.set("FACTORY_A");
        seed(counters, 1, "FACTORY_A", "F:FACTORY_A|DAY:" + date, 998);
        assertEquals("SO" + date + "999", service.getDocNo("SALES_ORDER"));
        assertEquals("SO" + date + "1000", service.getDocNo("SALES_ORDER"));
        assertEquals("SO" + date + "1001", service.getDocNo("SALES_ORDER"));
        assertTrue(service.previewDocNo("SALES_ORDER").endsWith("1002"));
        assertEquals(1001, counters.get("1:FACTORY_A:F:FACTORY_A|DAY:" + date).getLastSerialNo());

        var malformed = new ArrayList<>(entities("SO", 1));
        malformed.get(1).setPartLength(null);
        when(parts.selectByRuleIdForUpdate(1L)).thenReturn(malformed);
        assertThrows(ServiceException.class, () -> service.previewDocNo("SALES_ORDER"));
    }

    private static AutoCodeRuleSnapshotCache.RuleConfig config(long id, String code, String prefix) {
        return config(id, code, "WORK_ORDER", prefix);
    }

    private static AutoCodeRuleSnapshotCache.RuleConfig config(long id, String code, String type, String prefix) {
        return new AutoCodeRuleSnapshotCache.RuleConfig(id, code, type, code, 64, "N", null, null, "Y", List.of(
                new AutoCodeRuleSnapshotCache.PartConfig(id * 10, id, 1, "FIXCHAR", prefix.length(), null,
                        prefix, null, null, null, null),
                new AutoCodeRuleSnapshotCache.PartConfig(id * 10 + 1, id, 2, "NOWDATE", 8, "yyyyMMdd",
                        null, null, null, null, null),
                new AutoCodeRuleSnapshotCache.PartConfig(id * 10 + 2, id, 3, "SERIALNO", 3, null,
                        null, 1, 1, "Y", "DAY")));
    }

    private static AutoCodeRuleSnapshotCache.RuleConfig config(long id, String code, String type,
                                                                String factoryId, String prefix) {
        return new AutoCodeRuleSnapshotCache.RuleConfig(id, code, type, factoryId, code, 64, "N", null, null,
                "Y", List.of(
                new AutoCodeRuleSnapshotCache.PartConfig(id * 10, id, 1, "FIXCHAR", prefix.length(), null,
                        prefix, null, null, null, null),
                new AutoCodeRuleSnapshotCache.PartConfig(id * 10 + 1, id, 2, "NOWDATE", 8, "yyyyMMdd",
                        null, null, null, null, null),
                new AutoCodeRuleSnapshotCache.PartConfig(id * 10 + 2, id, 3, "SERIALNO", 3, null,
                        null, 1, 1, "Y", "DAY")));
    }

    private static SysAutoCodeGenerationServiceImpl service(AutoCodeRuleSnapshotCache cache,
            SysAutoCodeRuleMapper rules, SysAutoCodePartMapper parts, SysAutoCodeResultMapper results,
            AutoCodeExtraMapper extra, PlatformTransactionManager manager, AutoCodeFactoryContext factoryContext) {
        var handler = new PartTypeHandler(new PartTypeInputCharHandler(), new PartTypeNowDateHandler(),
                new PartTypeFixCharHandler(), new PartTypeSerialNoHandler());
        return new SysAutoCodeGenerationServiceImpl(cache, rules, parts, results, extra, handler, manager,
                factoryContext);
    }

    private static SysAutoCodeRule rule(long id) {
        var row = new SysAutoCodeRule();
        row.setRuleId(id);
        row.setRuleCode(id == 1 ? "SALES_ORDER" : "STOCK_RECEIPT");
        row.setRuleType(id == 1 ? "WORK_ORDER" : "STOCK_IN");
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

    private static void seed(Map<String, SysAutoCodeResult> counters, long ruleId, String factoryId, String cycle, int value) {
        var row = new SysAutoCodeResult();
        row.setRuleId(ruleId);
        row.setFactoryId(factoryId);
        row.setCycleKey(cycle);
        row.setLastSerialNo(value);
        row.setGenIndex(value);
        counters.put(ruleId + ":" + factoryId + ":" + cycle, row);
    }
}
