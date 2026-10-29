package dev.mesbase.system.autocode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

import dev.mesbase.common.exception.ServiceException;
import dev.mesbase.core.factory.FactoryId;
import dev.mesbase.core.factory.FactoryScope;
import dev.mesbase.platform.execution.AutoCodeFactoryContextAdapter;
import dev.mesbase.platform.execution.FactoryContext;
import dev.mesbase.system.mapper.AutoCodeExtraMapper;
import dev.mesbase.system.mapper.SysAutoCodePartMapper;
import dev.mesbase.system.mapper.SysAutoCodeResultMapper;
import dev.mesbase.system.mapper.SysAutoCodeRuleMapper;
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
import dev.mesbase.core.error.FoundationException;
import dev.mesbase.infrastructure.persistence.OwnedTransactionManager;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;

/** Explicit opt-in: existing dev tables only; second child fails and all inserts roll back.
 * It does not run migrations, create tables, or delete any counter or existing rule. */
@EnabledIfEnvironmentVariable(named = "AUTOCODE_MYSQL_ACCEPTANCE", matches = "true")
class AutoCodeRuleDocumentMySqlTest {
    @Test
    void realCenterRollbackAndForeignDatabaseTransactionRejection() throws Exception {
        String centerUrl = System.getenv("AUTOCODE_CENTER_URL");
        String businessUrl = System.getenv("AUTOCODE_BUSINESS_URL");
        if (centerUrl == null || !centerUrl.contains("/mes_base_dev?")) throw new IllegalArgumentException("Explicit dev center required");
        if (businessUrl == null || !businessUrl.contains("/mes_base_dev_perf_test?")) throw new IllegalArgumentException("Explicit configured business target required");
        String username = System.getenv("AUTOCODE_DB_USER");
        String password = System.getenv("AUTOCODE_DB_PASSWORD");
        try (var f = new AutoCodeRuleDocumentTest.Fixture(new DriverManagerDataSource(centerUrl, username, password), false)) {
            assertEquals("mes_base_dev", f.jdbc.queryForObject("SELECT DATABASE()", String.class));
            String code = "QA_AUTOCODE_ROLLBACK_20261029";
            assertEquals(0, f.jdbc.queryForObject("SELECT COUNT(*) FROM sys_auto_code_rule WHERE rule_code=?", Integer.class, code));
            var writes = new AtomicInteger();
            doAnswer(invocation -> {
                if (writes.incrementAndGet() == 2) throw new ServiceException("TEST rollback after actual first child insert");
                return invocation.callRealMethod();
            }).when(f.parts).add(any());
            assertThrows(ServiceException.class, () -> f.service.createDocument(AutoCodeRuleDocumentTest.rule(code), "QARB", "TEST"));
            assertEquals(2, writes.get());
            assertEquals(0, f.jdbc.queryForObject("SELECT COUNT(*) FROM sys_auto_code_rule WHERE rule_code=?", Integer.class, code));
            assertEquals(0, f.jdbc.queryForObject("SELECT COUNT(*) FROM sys_auto_code_part p LEFT JOIN sys_auto_code_rule r ON r.rule_id=p.rule_id WHERE r.rule_id IS NULL", Integer.class));
            var source = new DriverManagerDataSource(businessUrl, username, password);
            var business = new TransactionTemplate(new OwnedTransactionManager(source));
            business.setReadOnly(true);
            business.executeWithoutResult(status -> {
                assertEquals("mes_base_dev_perf_test", new JdbcTemplate(source).queryForObject("SELECT DATABASE()", String.class));
                assertThrows(FoundationException.class, () -> f.service.document(1L));
            });
        }
    }
    @Test
    @EnabledIfEnvironmentVariable(named = "AUTOCODE_ALLOCATE_SAMPLE", matches = "true")
    void existingSampleBusinessNumberUsesBoundFactory() throws Exception {
        String centerUrl = System.getenv("AUTOCODE_CENTER_URL");
        if (centerUrl == null || !centerUrl.contains("/mes_base_dev?")) throw new IllegalArgumentException("Explicit dev center required");
        var source = new DriverManagerDataSource(centerUrl, System.getenv("AUTOCODE_DB_USER"), System.getenv("AUTOCODE_DB_PASSWORD"));
        try (var f = new AutoCodeRuleDocumentTest.Fixture(source, false)) {
            String factoryId = "TEST_DEV_PERF_20261019";
            var binding = f.jdbc.queryForMap("SELECT f.status factory_status,f.runtime_mode,d.status datasource_status,d.db_host,d.db_port,d.db_name FROM mf_factory f JOIN mf_datasource d ON d.datasource_id=f.datasource_id WHERE f.factory_id=?", factoryId);
            assertEquals("ENABLED", binding.get("factory_status"));
            assertEquals("ENABLED", binding.get("datasource_status"));
            assertEquals("LOCAL", binding.get("runtime_mode"));
            assertEquals("mes_base_dev_perf_test", binding.get("db_name"));
            assertEquals("192.168.18.25", binding.get("db_host"));
            assertEquals(3306, ((Number) binding.get("db_port")).intValue());
            var platform = f.platform;
            var rules = platform.mapper(SysAutoCodeRuleMapper.class);
            var parts = platform.mapper(SysAutoCodePartMapper.class);
            var generation = new SysAutoCodeGenerationServiceImpl(new AutoCodeRuleSnapshotCache(f.redis, rules, parts),
                    rules, parts, platform.mapper(SysAutoCodeResultMapper.class), platform.mapper(AutoCodeExtraMapper.class),
                    new PartTypeHandler(new PartTypeInputCharHandler(), new PartTypeNowDateHandler(), new PartTypeFixCharHandler(), new PartTypeSerialNoHandler()),
                    platform.transactionManager(), new AutoCodeFactoryContextAdapter());
            String expected = System.getenv("AUTOCODE_EXPECTED_PREFIX")
                    + LocalDate.now(ZoneId.of("Asia/Shanghai")).format(DateTimeFormatter.BASIC_ISO_DATE)
                    + System.getenv("AUTOCODE_EXPECTED_SERIAL");
            FactoryContext.withScope(new FactoryScope(new FactoryId(factoryId)), () -> {
                assertEquals(expected, generation.previewDocNo("QA_AUTOCODE_20261029"));
                assertEquals(expected, generation.getDocNo("QA_AUTOCODE_20261029"));
                return null;
            });
            System.out.println("ACTUAL_MYSQL_FACTORY_NUMBER=" + expected);
        }
    }

}
