package dev.mesbase.system.autocode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.mesbase.system.dto.AutoCodeRuleDocumentForm;
import dev.mesbase.web.controller.system.SysAutoCodeRuleController;
import org.springframework.test.util.ReflectionTestUtils;
import dev.mesbase.common.core.domain.entity.SysAutoCodePart;
import dev.mesbase.common.core.domain.entity.SysAutoCodeResult;
import dev.mesbase.common.core.domain.entity.SysAutoCodeRule;
import dev.mesbase.common.core.redis.RedisCache;
import dev.mesbase.common.exception.ServiceException;
import dev.mesbase.infrastructure.persistence.PlatformPersistence;
import dev.mesbase.system.mapper.AutoCodeExtraMapper;
import dev.mesbase.system.mapper.SysAutoCodePartMapper;
import dev.mesbase.system.mapper.SysAutoCodeResultMapper;
import dev.mesbase.system.mapper.SysAutoCodeRuleMapper;
import dev.mesbase.system.service.AutoCodeRuleReferenceGuard;
import dev.mesbase.system.service.IAutoCodeRuleService;
import dev.mesbase.system.service.impl.AutoCodeRuleSnapshotCache;
import dev.mesbase.system.service.impl.SysAutoCodeGenerationServiceImpl;
import dev.mesbase.system.service.impl.SysAutoCodeRuleServiceImpl;
import dev.mesbase.system.strategy.PartTypeFixCharHandler;
import dev.mesbase.system.strategy.PartTypeHandler;
import dev.mesbase.system.strategy.PartTypeInputCharHandler;
import dev.mesbase.system.strategy.PartTypeNowDateHandler;
import dev.mesbase.system.strategy.PartTypeSerialNoHandler;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.sql.Connection;
import org.apache.ibatis.executor.statement.StatementHandler;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Signature;
import org.apache.ibatis.reflection.SystemMetaObject;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.PlatformTransactionManager;

/** In-memory SQL/owned Spring transaction evidence, separate from real MySQL acceptance. */
public class AutoCodeRuleDocumentTest {
    @Intercepts(@Signature(type = StatementHandler.class, method = "prepare", args = {Connection.class, Integer.class}))
    static class H2DateFunction implements Interceptor {
        public Object intercept(Invocation invocation) throws Throwable {
            var bound = ((StatementHandler) invocation.getTarget()).getBoundSql();
            SystemMetaObject.forObject(bound).setValue("sql", bound.getSql().replace("sysdate()", "CURRENT_TIMESTAMP"));
            return invocation.proceed();
        }
    }
    @Configuration
    @EnableTransactionManagement
    static class Transactions { }

    static class Fixture implements AutoCloseable {
        final AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        final JdbcTemplate jdbc;
        final PlatformPersistence platform;
        final SysAutoCodePartMapper parts;
        final RedisCache redis = mock(RedisCache.class);
        final AutoCodeRuleReferenceGuard references = mock(AutoCodeRuleReferenceGuard.class);
        final IAutoCodeRuleService service;
        final SysAutoCodeGenerationServiceImpl numbering;

        Fixture() throws Exception {
            this(new DriverManagerDataSource("jdbc:h2:mem:autocode_" + UUID.randomUUID()
                    + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", ""), true);
        }

        Fixture(DriverManagerDataSource source, boolean initializeH2) throws Exception {
            when(redis.setCacheObjectIfVersion(anyString(), nullable(String.class), anyString(), any(), anyInt())).thenReturn(true);
            jdbc = new JdbcTemplate(source);
            if (initializeH2) {
            String schema = new ClassPathResource("db/system/V6__source_autocode_rules.sql")
                    .getContentAsString(StandardCharsets.UTF_8).split("INSERT INTO sys_menu")[0];
            try (var connection = source.getConnection()) {
                ScriptUtils.executeSqlScript(connection, new ByteArrayResource(schema.getBytes(StandardCharsets.UTF_8)));
            }
            jdbc.execute("ALTER TABLE sys_auto_code_rule ADD rule_type VARCHAR(32)");
            jdbc.execute("ALTER TABLE sys_auto_code_rule ADD factory_id VARCHAR(64)");
            jdbc.execute("ALTER TABLE sys_auto_code_result DROP CONSTRAINT uk_autocode_counter");
            jdbc.execute("ALTER TABLE sys_auto_code_result ADD factory_id VARCHAR(64) DEFAULT '' NOT NULL");
            jdbc.execute("CREATE UNIQUE INDEX uk_autocode_counter ON sys_auto_code_result(rule_id,factory_id,cycle_key)");
            }
            platform = new PlatformPersistence(source, Set.of(SysAutoCodeRuleMapper.class,
                    SysAutoCodePartMapper.class, SysAutoCodeResultMapper.class, AutoCodeExtraMapper.class), config -> {
                if (initializeH2) config.addInterceptor(new H2DateFunction());
                config.getTypeAliasRegistry().registerAlias(SysAutoCodeRule.class);
                config.getTypeAliasRegistry().registerAlias(SysAutoCodePart.class);
                config.getTypeAliasRegistry().registerAlias(SysAutoCodeResult.class);
            });
            var rules = platform.mapper(SysAutoCodeRuleMapper.class);
            parts = spy(platform.mapper(SysAutoCodePartMapper.class));
            var extra = platform.mapper(AutoCodeExtraMapper.class);
            var cache = new AutoCodeRuleSnapshotCache(redis, rules, parts);
            context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("test", Map.of("mes.system.enabled", "true")));
            context.register(Transactions.class);
            context.registerBean("masterTransactionManager", PlatformTransactionManager.class, () -> platform.transactionManager());
            context.registerBean(SysAutoCodeRuleServiceImpl.class,
                    () -> new SysAutoCodeRuleServiceImpl(rules, extra, cache, references, parts, platform.transactionManager()));
            context.refresh();
            service = context.getBean(IAutoCodeRuleService.class);
            numbering = new SysAutoCodeGenerationServiceImpl(cache, rules, parts,
                    platform.mapper(SysAutoCodeResultMapper.class), extra,
                    new PartTypeHandler(new PartTypeInputCharHandler(), new PartTypeNowDateHandler(),
                            new PartTypeFixCharHandler(), new PartTypeSerialNoHandler()),
                    platform.transactionManager(), () -> "FACTORY_TEST");
        }
        public void close() { context.close(); }
    }

    @Test
    void savesCompleteCompositionThenEditsPrefixWithoutResettingFactoryOrPlatformCounters() throws Exception {
        try (var f = new Fixture()) {
            var doc = f.service.createDocument(rule("DOC_A"), "QA", "TEST");
            assertEquals(List.of("FIXCHAR", "NOWDATE", "SERIALNO"), doc.parts().stream().map(SysAutoCodePart::getPartType).toList());
            assertTrue(doc.parts().stream().allMatch(p -> doc.rule().getRuleId().equals(p.getRuleId())));
            var partIds = doc.parts().stream().map(SysAutoCodePart::getPartId).toList();
            String date = LocalDate.now(ZoneId.of("Asia/Shanghai")).format(DateTimeFormatter.BASIC_ISO_DATE);
            assertEquals("QA" + date + "001", f.numbering.previewDocNo("DOC_A"));
            assertEquals(0, f.jdbc.queryForObject("SELECT COUNT(*) FROM sys_auto_code_result", Integer.class));
            assertEquals("QA" + date + "001", f.numbering.getDocNo("DOC_A"));
            assertEquals("QA" + date + "001", f.numbering.generate("DOC_A", null));
            var editedRule = doc.rule();
            editedRule.setRuleName("修改名称");
            var beforeCounters = f.jdbc.queryForList("SELECT * FROM sys_auto_code_result ORDER BY code_id");
            var updated = f.service.updateDocument(editedRule, "QB", doc.version(), "TEST");
            assertEquals(beforeCounters, f.jdbc.queryForList("SELECT * FROM sys_auto_code_result ORDER BY code_id"));
            assertEquals(doc.rule().getRuleId(), updated.rule().getRuleId());
            assertEquals(partIds, updated.parts().stream().map(SysAutoCodePart::getPartId).toList());
            assertEquals("QB" + date + "002", f.numbering.previewDocNo("DOC_A"));
            assertEquals("QB" + date + "002", f.numbering.getDocNo("DOC_A"));
            assertEquals("QB" + date + "002", f.numbering.generate("DOC_A", null));
            assertEquals(2, f.jdbc.queryForObject("SELECT COUNT(*) FROM sys_auto_code_result", Integer.class));
            assertThrows(ServiceException.class, () -> f.service.updateDocument(editedRule, "QC", doc.version(), "TEST"));
            verify(f.references, never()).requireUnreferenced(any());
            verify(f.redis, times(2)).invalidateVersionedObject(anyString(), anyString(), anyString());
        }
    }

    @Test
    void childInsertFailureRollsBackHeaderAndPreviouslyInsertedChildWithoutCachePublication() throws Exception {
        try (var f = new Fixture()) {
            var writes = new AtomicInteger();
            doAnswer(invocation -> {
                if (writes.incrementAndGet() == 2) throw new ServiceException("TEST second child failed");
                return invocation.callRealMethod();
            }).when(f.parts).add(any());
            assertThrows(ServiceException.class, () -> f.service.createDocument(rule("ROLLBACK"), "RB", "TEST"));
            assertEquals(0, f.jdbc.queryForObject("SELECT COUNT(*) FROM sys_auto_code_rule", Integer.class));
            assertEquals(0, f.jdbc.queryForObject("SELECT COUNT(*) FROM sys_auto_code_part", Integer.class));
            verify(f.redis, never()).invalidateVersionedObject(anyString(), anyString(), anyString());
        }
    }

    @Test
    void prefixUpdateFailureAlsoRollsBackHeaderEdit() throws Exception {
        try (var f = new Fixture()) {
            var doc = f.service.createDocument(rule("EDIT_ROLLBACK"), "EB", "TEST");
            doc.rule().setRuleName("应回滚");
            doAnswer(invocation -> { throw new ServiceException("TEST prefix write failed"); })
                    .when(f.parts).updateSysAutoCodePart(any());
            assertThrows(ServiceException.class, () -> f.service.updateDocument(doc.rule(), "EC", doc.version(), "TEST"));
            assertEquals("EDIT_ROLLBACK", f.service.document(doc.rule().getRuleId()).rule().getRuleName());
            assertEquals("EB", f.service.document(doc.rule().getRuleId()).parts().getFirst().getFixCharacter());
        }
    }

    @Test
    void advancedCompositionIsPreservedByHeaderOnlyEditAndCannotBeSilentlyStandardized() throws Exception {
        try (var f = new Fixture()) {
            var doc = f.service.createDocument(rule("ADVANCED"), "AD", "TEST");
            f.jdbc.update("UPDATE sys_auto_code_part SET date_format='yyyyMM',part_length=6 WHERE rule_id=? AND part_type='NOWDATE'", doc.rule().getRuleId());
            var advanced = f.service.document(doc.rule().getRuleId());
            assertTrue(!advanced.prefixEditable());
            advanced.rule().setRuleName("高级名称");
            var saved = f.service.updateDocument(advanced.rule(), null, advanced.version(), "TEST");
            assertEquals("yyyyMM", saved.parts().get(1).getDateFormat());
            assertThrows(ServiceException.class, () -> f.service.updateDocument(saved.rule(), "NEW", saved.version(), "TEST"));
        }
    }

    @Test
    void controllerDtoRepairAttributesNewPartsToCurrentActorAndPreservesOriginalAuditAndCounters() throws Exception {
        try (var f = new Fixture()) {
            var rule = rule("EMPTY");
            rule.setCreateBy("AUTHOR_A");
            f.service.insertInfo(rule);
            var empty = f.service.document(rule.getRuleId());
            assertTrue(empty.prefixEditable());
            assertTrue(empty.parts().isEmpty());
            f.jdbc.update("INSERT INTO sys_auto_code_result(rule_id,factory_id,cycle_key,gen_date,gen_index,last_result,last_serial_no) VALUES(?,?,?,?,?,?,?)",
                    rule.getRuleId(), "FACTORY_TEST", "F:FACTORY_TEST|DAY:20260101", "20260101120000", 42, "OLD20260101042", 42);
            var counters = f.jdbc.queryForList("SELECT * FROM sys_auto_code_result ORDER BY code_id");
            // The real HTTP DTO discards even a caller-supplied original author/update identity.
            var input = new ObjectMapper().readValue("""
                    {"rule":{"ruleId":"%s","ruleCode":"EMPTY","ruleName":"EMPTY",
                    "ruleType":"WORK_ORDER","factoryId":null,"maxLength":64,"isPadded":"N",
                    "enableFlag":"Y","createBy":"AUTHOR_A","updateBy":"SPOOFED"},
                    "prefix":"EM","version":"%s"}
                    """.formatted(rule.getRuleId(), empty.version()), AutoCodeRuleDocumentForm.class);
            assertNull(input.rule().toEntity().getCreateBy());
            assertNull(input.rule().toEntity().getUpdateBy());
            var controller = spy(new SysAutoCodeRuleController());
            ReflectionTestUtils.setField(controller, "iAutoCodeRuleService", f.service);
            doReturn("EDITOR_B").when(controller).getUsername();
            assertEquals(200, controller.updateInfo(input).get("code"));

            var repaired = f.service.document(rule.getRuleId());
            assertEquals(rule.getRuleId(), repaired.rule().getRuleId());
            assertEquals("AUTHOR_A", repaired.rule().getCreateBy());
            assertEquals(empty.rule().getCreateTime(), repaired.rule().getCreateTime());
            assertEquals("EDITOR_B", repaired.rule().getUpdateBy());
            assertEquals(3, repaired.parts().size());
            assertTrue(repaired.parts().stream().allMatch(part -> "EDITOR_B".equals(part.getCreateBy())));
            assertEquals(counters, f.jdbc.queryForList("SELECT * FROM sys_auto_code_result ORDER BY code_id"));
        }
    }

    static SysAutoCodeRule rule(String code) {
        var rule = new SysAutoCodeRule();
        rule.setRuleCode(code); rule.setRuleName(code); rule.setRuleType("WORK_ORDER");
        rule.setMaxLength(64); rule.setIsPadded("N"); rule.setEnableFlag("Y"); rule.setCreateBy("TEST");
        return rule;
    }
}
