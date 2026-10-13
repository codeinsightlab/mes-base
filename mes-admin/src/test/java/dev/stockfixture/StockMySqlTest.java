package dev.stockfixture;

import dev.mesbase.business.wm.dao.WmInventoryMapper;
import dev.mesbase.business.wm.dao.WmWarehouseMapper;
import dev.mesbase.business.wm.domain.WmInventoryEntity;
import dev.mesbase.business.wm.domain.WmInventoryExample;
import dev.mesbase.business.wm.domain.WmWarehouseEntity;
import dev.mesbase.business.wm.domain.WmWarehouseExample;
import java.math.BigDecimal;
import dev.mesbase.acceptance.AcceptanceBusinessDatabase;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
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
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * LIVE MYSQL: generated SQL in the authorized development database. Test rows are rolled back.
 */
@EnabledIfEnvironmentVariable(named = "MES_STOCK_MYSQL_ACCEPTANCE", matches = "true")
class StockMySqlTest {
    @Test
    void realMetadataDecimalAndUniqueKeys() throws Exception {
        var source = AcceptanceBusinessDatabase.source();
        var configuration = new Configuration(new Environment("LIVE MYSQL ROLLBACK ONLY", new JdbcTransactionFactory(), source));
        configuration.addMapper(WmWarehouseMapper.class);
        configuration.addMapper(WmInventoryMapper.class);
        var factory = new SqlSessionFactoryBuilder().build(configuration);
        String scope = "TEST_STOCK_" + UUID.randomUUID();
        try (var session = factory.openSession(false)) {
            session.getConnection().setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
            var warehouses = session.getMapper(WmWarehouseMapper.class);
            var inventories = session.getMapper(WmInventoryMapper.class);
            var warehouse = new WmWarehouseEntity();

            warehouse.setCode(scope);
            warehouse.setName("事务回滚验收仓库");
            warehouse.setStatus("ENABLED");
            assertEquals(1, warehouses.insert(warehouse));
            assertThrows(RuntimeException.class, () -> warehouses.insert(warehouse));
            var stock = new WmInventoryEntity();

            stock.setMaterialId(999999999L);
            stock.setWarehouseId(warehouse.getId());
            stock.setQuantity(new BigDecimal("99999999999999.1234"));
            stock.setStatus("ENABLED");
            assertEquals(1, inventories.insert(stock));
            assertEquals(new BigDecimal("99999999999999.1234"), inventories.selectByPrimaryKey(stock.getId())
                    .getQuantity());
            stock.setId(null);
            assertThrows(RuntimeException.class, () -> inventories.insert(stock));
            var filter = new WmInventoryExample();
            filter.createCriteria().andWarehouseIdEqualTo(warehouse.getId());
            assertEquals(1, inventories.countByExample(filter));
            session.rollback();
        }
        try (var session = factory.openSession()) {
            var filter = new WmWarehouseExample();
            filter.createCriteria().andCodeEqualTo(scope);
            assertEquals(0, session.getMapper(WmWarehouseMapper.class).countByExample(filter));
        }
    }
}
