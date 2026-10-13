package dev.issuefixture;

import dev.mesbase.business.issue.dao.MesMaterialIssueMapper;
import dev.mesbase.business.issue.dao.MesMaterialIssueItemMapper;
import dev.mesbase.business.issue.domain.MesMaterialIssueEntity;
import dev.mesbase.business.issue.domain.MesMaterialIssueExample;
import dev.mesbase.business.issue.domain.MesMaterialIssueItemEntity;
import dev.mesbase.business.issue.domain.MesMaterialIssueItemExample;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.yaml.snakeyaml.Yaml;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * LIVE MYSQL: generated XML CRUD and metadata; every test row is rolled back.
 */
@EnabledIfEnvironmentVariable(named = "MES_ISSUE_MYSQL_ACCEPTANCE", matches = "true")
class MaterialIssueMySqlTest {
    @Test
    void metadataAndCrudPreserveSixDecimalPlacesWithoutGuessedUniqueKeys() throws Exception {
        Map<?, ?> root = new Yaml().load(Files.readString(Path.of("mes-admin/src/main/resources/application-dev.yml")));
        Map<?, ?> mes = (Map<?, ?>) root.get("mes");
        Map<?, ?> codegen = (Map<?, ?>) mes.get("codegen");
        Map<?, ?> profiles = (Map<?, ?>) codegen.get("profiles");
        Map<?, ?> profile = (Map<?, ?>) profiles.get("dev");
        var source = new DriverManagerDataSource((String) profile.get("url"),
                (String) profile.get("username"), (String) profile.get("password"));
        var config = new Configuration(new Environment("LIVE MYSQL ROLLBACK ONLY", new JdbcTransactionFactory(), source));
        config.addMapper(MesMaterialIssueMapper.class);
        config.addMapper(MesMaterialIssueItemMapper.class);
        var sessions = new SqlSessionFactoryBuilder().build(config);
        String scope = "TEST_ISSUE_" + UUID.randomUUID();
        try (var session = sessions.openSession(false)) {
            var connection = session.getConnection();
            assertEquals("mes_base_dev_business", connection.getCatalog());
            try (var statement = connection.createStatement()) {
                try (var rows = statement.executeQuery("SELECT COLUMN_COMMENT FROM information_schema.columns WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME IN ('mes_material_issue','mes_material_issue_item')")) {
                    int count = 0;
                    while (rows.next()) {
                        assertTrue(rows.getString(1) != null && !rows.getString(1).isBlank());
                        count++;
                    }
                    assertEquals(32, count);
                }
                try (var indexes = statement.executeQuery("SELECT COUNT(*) FROM information_schema.statistics WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME IN ('mes_material_issue','mes_material_issue_item') AND NON_UNIQUE=0 AND INDEX_NAME<>'PRIMARY'")) {
                    indexes.next();
                    assertEquals(0, indexes.getInt(1));
                }
            }
            var headers = session.getMapper(MesMaterialIssueMapper.class);
            var items = session.getMapper(MesMaterialIssueItemMapper.class);
            var header = new MesMaterialIssueEntity();
            header.setFactoryId(scope);
            header.setIssueNo("TEST_ONLY");
            header.setStatus("DRAFT");
            assertEquals(1, headers.insert(header));
            var item = new MesMaterialIssueItemEntity();
            item.setFactoryId(scope);
            item.setIssueId(header.getId());
            item.setMaterialId(999999999L);
            item.setMaterialCode("TEST_ONLY");
            item.setBatchNo("批次/不校验格式");
            item.setQuantity(new BigDecimal("99999999999999.123456"));
            assertEquals(1, items.insert(item));
            assertEquals(item.getQuantity(), items.selectByPrimaryKey(item.getId()).getQuantity());
            item.setId(null);
            assertEquals(1, items.insert(item));
            item.setQuantity(new BigDecimal("1.000001"));
            assertEquals(1, items.updateByPrimaryKey(item));
            assertEquals(new BigDecimal("1.000001"), items.selectByPrimaryKey(item.getId()).getQuantity());
            var filter = new MesMaterialIssueItemExample();
            filter.createCriteria().andFactoryIdEqualTo(scope).andIssueIdEqualTo(header.getId());
            assertEquals(2, items.countByExample(filter));
            assertEquals(2, items.deleteByExample(filter));
            assertEquals(1, headers.deleteByPrimaryKey(header.getId()));
            session.rollback();
        }
        try (var session = sessions.openSession()) {
            var filter = new MesMaterialIssueExample();
            filter.createCriteria().andFactoryIdEqualTo(scope);
            assertEquals(0, session.getMapper(MesMaterialIssueMapper.class).countByExample(filter));
        }
    }
}
