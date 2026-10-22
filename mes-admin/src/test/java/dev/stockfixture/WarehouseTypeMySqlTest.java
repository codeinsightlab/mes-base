package dev.stockfixture;

import dev.mesbase.business.wm.dao.WmWarehouseMapper;
import dev.mesbase.business.shared.enums.BusinessSource;
import dev.mesbase.business.shared.enums.EnableStatus;
import dev.mesbase.business.wm.domain.WarehouseType;
import dev.mesbase.business.wm.domain.WmWarehouseEntity;
import dev.mesbase.business.wm.domain.WmWarehouseExample;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.yaml.snakeyaml.Yaml;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * LIVE DEVELOPMENT MYSQL ONLY: generated persistence and rollback, without starting or restarting a service.
 */
@EnabledIfEnvironmentVariable(named = "MES_WAREHOUSE_TYPE_MYSQL", matches = "true")
class WarehouseTypeMySqlTest {
    @Test
    void verifiedV48MetadataAndSevenTypesRoundTripWithoutRetainingTestRows() throws Exception {
        Map<?, ?> root = new Yaml().load(Files.readString(Path.of(".local/codegen-simple-b.yml")));
        Map<?, ?> profiles = (Map<?, ?>) ((Map<?, ?>) ((Map<?, ?>) root.get("mes")).get("codegen")).get("profiles");
        Map<?, ?> profile = (Map<?, ?>) profiles.get("acceptance");
        String url = (String) profile.get("url");
        assertTrue(url.startsWith("jdbc:mysql://192.168.18.25:3306/mes_base_dev_business_simple_b?"));
        var source = new DriverManagerDataSource(url, (String) profile.get("username"), (String) profile.get("password"));
        var jdbc = new JdbcTemplate(source);
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM mes_base_dev.mf_factory f JOIN mes_base_dev.mf_datasource d ON f.datasource_id=d.datasource_id WHERE f.factory_id='SIMPLE_B' AND f.status='ENABLED' AND d.status='ENABLED' AND d.db_host='192.168.18.25' AND d.db_port=3306 AND d.db_name=DATABASE()", Integer.class));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM flyway_business_schema_history WHERE version='48' AND success=1", Integer.class));
        assertEquals("YES", jdbc.queryForObject("SELECT is_nullable FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='wm_warehouse' AND column_name='warehouse_type' AND character_maximum_length=32", String.class));
        long before = jdbc.queryForObject("SELECT COUNT(*) FROM wm_warehouse", Long.class);
        var balances = jdbc.queryForList("SELECT COUNT(*) AS records, SUM(quantity) AS quantity, SUM(available_quantity) AS available FROM wm_inventory");
        var documents = jdbc.queryForList("SELECT COUNT(*) AS records, SUM(quantity) AS quantity FROM wm_stock_document_line");
        var config = new Configuration(new Environment("TEST_ONLY_VERIFIED_SIMPLE_B", new JdbcTransactionFactory(), source));
        String resource = "dev/mesbase/business/wm/dao/WmWarehouseMapper.xml";
        try (var xml = getClass().getClassLoader().getResourceAsStream(resource)) {
            new XMLMapperBuilder(xml, config, resource, config.getSqlFragments()).parse();
        }
        String prefix = "TEST_TYPE_" + UUID.randomUUID().toString().substring(0, 8);
        try (var session = new SqlSessionFactoryBuilder().build(config).openSession(false)) {
            try {
                var mapper = session.getMapper(WmWarehouseMapper.class);
                var old = new WmWarehouseExample();
                old.createCriteria().andWarehouseTypeIsNull();
                for (var row : mapper.selectByExample(old)) assertNull(row.getWarehouseType());
                for (WarehouseType type : WarehouseType.values()) {
                    var row = new WmWarehouseEntity();
                    row.setCode(prefix + type.getValue());
                    row.setName("TEST ONLY warehouse type");
                    row.setSource(BusinessSource.LOCAL.getValue());
                    row.setStatus(EnableStatus.ENABLED.getValue());
                    row.setWarehouseType(type.getValue());
                    assertEquals(1, mapper.insert(row));
                    assertEquals(type.getValue(), mapper.selectByPrimaryKey(row.getId()).getWarehouseType());
                    var filter = new WmWarehouseExample();
                    filter.createCriteria().andCodeLike(prefix + "%").andWarehouseTypeEqualTo(type.getValue());
                    assertEquals(1, mapper.selectByExample(filter).size());
                    row.setWarehouseType(WarehouseType.INSPECTION.getValue());
                    assertEquals(1, mapper.updateByPrimaryKey(row));
                    assertEquals(WarehouseType.INSPECTION.getValue(), mapper.selectByPrimaryKey(row.getId())
                            .getWarehouseType());
                    // Restore to keep the subsequent per-type test set distinct.
                    row.setWarehouseType(type.getValue());
                    mapper.updateByPrimaryKey(row);
                }
            } finally {
                session.rollback();
            }
        }
        assertEquals(before, jdbc.queryForObject("SELECT COUNT(*) FROM wm_warehouse", Long.class));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM wm_warehouse WHERE code LIKE ?", Integer.class, prefix + "%"));
        assertEquals(balances, jdbc.queryForList("SELECT COUNT(*) AS records, SUM(quantity) AS quantity, SUM(available_quantity) AS available FROM wm_inventory"));
        assertEquals(documents, jdbc.queryForList("SELECT COUNT(*) AS records, SUM(quantity) AS quantity FROM wm_stock_document_line"));
    }
}
