package dev.issuefixture;

import dev.mesbase.business.wm.dao.WmStockDocumentMapper;
import dev.mesbase.business.wm.domain.WmStockDocumentExample;
import dev.mesbase.business.wm.domain.StockDocumentType;
import dev.mesbase.common.core.domain.entity.SysDictData;
import dev.mesbase.common.utils.DictDataLookup;
import dev.mesbase.core.factory.FactoryId;
import dev.mesbase.core.error.FoundationException;
import dev.mesbase.core.error.ErrorCode;
import dev.mesbase.core.factory.FactoryScope;
import dev.mesbase.infrastructure.persistence.OwnedTransactionManager;
import dev.mesbase.infrastructure.persistence.PlatformPersistence;
import dev.mesbase.platform.execution.FactoryContext;
import dev.mesbase.system.mapper.SysDictDataMapper;
import dev.mesbase.system.service.AutoCodeNumbering;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

import static dev.issuefixture.ProductionCoreV1HttpTest.full;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** H2 HTTP regression with actual factory routing, transaction ownership and SQL statement guards.
 * Number formatting is a test double; its center reads/writes use the real ownership manager. */
class StockDocumentOwnedTransactionsHttpTest {
    @Configuration
    static class GuardedCenter {
        @Bean
        @Primary
        PlatformPersistence guardedPlatform() {
            var source = new DriverManagerDataSource("jdbc:h2:mem:stock_center_" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
            var jdbc = new JdbcTemplate(source);
            jdbc.execute("CREATE TABLE sys_dict_data(dict_code BIGINT PRIMARY KEY,dict_sort BIGINT,dict_label VARCHAR(80),dict_value VARCHAR(64),dict_type VARCHAR(80),css_class VARCHAR(64),list_class VARCHAR(64),is_default VARCHAR(1),status VARCHAR(1),create_by VARCHAR(64),create_time TIMESTAMP,remark VARCHAR(64))");
            int id = 0;
            for (var type : StockDocumentType.values()) {
                String dictionary = "IN".equals(type.getDirection().getValue()) ? "stock_document_in_type" : "stock_document_out_type";
                jdbc.update("INSERT INTO sys_dict_data(dict_code,dict_sort,dict_label,dict_value,dict_type,status) VALUES(?,?,?,?,?,'0')", ++id, id, type.getValue(), type.getValue(), dictionary);
            }
            return new PlatformPersistence(source, Set.of(SysDictDataMapper.class), c -> c.getTypeAliasRegistry().registerAlias(SysDictData.class));
        }

        @Bean
        @Primary
        DictDataLookup guardedDictionary(PlatformPersistence platform) {
            // Deliberately no independent-read wrapper here: the product boundary must suspend or
            // deliberately isolate business ownership, otherwise the real statement guard rejects it.
            return platform.mapper(SysDictDataMapper.class)::selectDictDataByType;
        }

        @Bean
        AtomicInteger guardedAllocations() { return new AtomicInteger(); }

        @Bean
        @Primary
        AutoCodeNumbering guardedNumbering(PlatformPersistence platform, AtomicInteger guardedAllocations) {
            return new AutoCodeNumbering() {
                private final AtomicInteger serial = guardedAllocations;
                public String getDocNo(String type) { return getBusinessDocNo(type, "STOCK_OUT"); }
                public String getDocNo(String type, String category) { return getBusinessDocNo(type, category); }
                public String getBusinessDocNo(String type, String category) {
                    return platform.run(() -> {
                        platform.mapper(SysDictDataMapper.class).selectDictDataByType("stock_document_in_type");
                        return "OWNED_TEST_" + serial.incrementAndGet();
                    });
                }
                public String previewBusinessDocNo(String type, String category) {
                    return platform.readOnly(s -> {
                        s.mapper(SysDictDataMapper.class).selectDictDataByType("stock_document_in_type");
                        return "OWNED_PREVIEW";
                    });
                }
                public List<RuleOption> rules(String category) {
                    return platform.readOnly(s -> s.mapper(SysDictDataMapper.class)
                            .selectDictDataByType("STOCK_IN".equals(category) ? "stock_document_in_type" : "stock_document_out_type")
                            .stream().map(row -> new RuleOption(row.getDictValue(), row.getDictLabel(), "TEST")).toList());
                }
                public String uniqueRuleCode(String category) { return rules(category).getFirst().ruleCode(); }
            };
        }
    }

    @Test
    void pagePinsTheSelectedFactoryForBothDirections() throws Exception {
        try (var context = ProductionInputHttpTest.context(GuardedCenter.class)) {
            var mvc = MaterialIssueHttpTest.mvc(context);
            for (String direction : List.of("IN", "OUT"))
                mvc.perform(full(get(StockDocumentHttpTest.ROOT).param("direction", direction))).andExpect(status().isOk());
        }
    }

    @Test
    void stockReadMetadataAndAggregateBoundariesUseRealDatabaseOwners() throws Exception {
        try (var context = ProductionInputHttpTest.context(GuardedCenter.class)) {
            var mvc = MaterialIssueHttpTest.mvc(context);
            String root = StockDocumentHttpTest.ROOT;
            for (String direction : List.of("IN", "OUT")) {
                mvc.perform(full(get(root + "/types").param("direction", direction))).andExpect(status().isOk());
                mvc.perform(full(get(root + "/numbering-rules").param("direction", direction))).andExpect(status().isOk());
            }
            mvc.perform(full(get(root + "/numbering-rules/preview").param("direction", "IN").param("typeKey", "OTHER_RECEIPT")))
                    .andExpect(status().isOk());
            String body = "{\"header\":{\"direction\":\"IN\",\"documentType\":\"OTHER_RECEIPT\",\"reason\":\"guarded aggregate\",\"ruleCode\":\"OTHER_RECEIPT\"},\"items\":[{\"stockId\":\"1\",\"materialId\":\"1\",\"warehouseId\":\"1\",\"unitId\":\"1\",\"batchNo\":\"L001\",\"binCode\":\"BIN1\",\"quantity\":\"1\"}]}";
            var saved = new ObjectMapper().readTree(mvc.perform(full(post(root + "/aggregate")).content(body))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
            mvc.perform(full(get(root + "/" + saved.get("document").get("id").asText()))).andExpect(status().isOk());
            String command = ProductionInputHttpTest.command(mvc, UUID.randomUUID().toString(), "L001", "10");
            var input = new ObjectMapper().readTree(ProductionInputHttpTest.submit(mvc, command));
            mvc.perform(full(get(root + "/" + input.get("documentId").asText() + "/production-input-basis")))
                    .andExpect(status().isOk());
        }
    }

    @Test
    void fixtureRejectsUnpinnedBusinessReadsAndForeignCenterOwnership() throws Exception {
        try (var context = ProductionInputHttpTest.context(GuardedCenter.class)) {
            FactoryContext.bind(new FactoryScope(new FactoryId("A")));
            try {
                var mapper = context.getBean(WmStockDocumentMapper.class);
                var platform = context.getBean(PlatformPersistence.class);
                assertCode(ErrorCode.FACTORY_SCOPE_REQUIRED, () -> mapper.countByExample(new WmStockDocumentExample()));
                var business = new TransactionTemplate(context.getBean("businessTransactionManager", OwnedTransactionManager.class));
                business.executeWithoutResult(status -> {
                    assertEquals(0, mapper.countByExample(new WmStockDocumentExample()));
                    assertCode(ErrorCode.CONFLICT, () -> platform.mapper(SysDictDataMapper.class).selectDictDataByType("stock_document_in_type"));
                    assertCode(ErrorCode.CONFLICT, () -> platform.run(() -> "foreign write transaction"));
                    assertEquals(true, !platform.readOnly(s -> s.mapper(SysDictDataMapper.class).selectDictDataByType("stock_document_in_type")).isEmpty());
                    assertEquals(0, mapper.countByExample(new WmStockDocumentExample()));
                });
            } finally {
                FactoryContext.clear();
            }
        }
    }
    private static void assertCode(ErrorCode expected, Runnable work) {
        Throwable failure = assertThrows(RuntimeException.class, work::run);
        while (!(failure instanceof FoundationException) && failure.getCause() != null) failure = failure.getCause();
        assertEquals(FoundationException.class, failure.getClass());
        assertEquals(expected, ((FoundationException) failure).code());
    }
}
