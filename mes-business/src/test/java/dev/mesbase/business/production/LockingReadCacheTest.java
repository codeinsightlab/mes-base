package dev.mesbase.business.production;

import dev.mesbase.business.production.dao.WorkorderMapper;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * MyBatis cache evidence only; MySQL contention is verified separately in the isolated DEV database.
 */
class LockingReadCacheTest {
    @Test
    void lockingReadCannotReuseAPreviouslyCachedOrdinaryRead() throws Exception {
        var source = new JdbcDataSource();
        source.setURL("jdbc:h2:mem:locking_read_cache;MODE=MySQL;DB_CLOSE_DELAY=-1");
        try (var connection = source.getConnection(); var sql = connection.createStatement()) {
            sql.execute("CREATE TABLE workorder (id BIGINT PRIMARY KEY, code VARCHAR(64), "
                    + "product_id BIGINT, planned_qty DECIMAL(20,6), routing_id BIGINT, status VARCHAR(32), "
                    + "planned_start_at TIMESTAMP, planned_end_at TIMESTAMP, remark VARCHAR(255), "
                    + "revision INT, create_time TIMESTAMP, update_time TIMESTAMP, source VARCHAR(32), bom_id BIGINT, bom_revision BIGINT, material_revision BIGINT DEFAULT 0, material_warehouse_ids VARCHAR(2000), material_work_center VARCHAR(100), material_status VARCHAR(16), material_frozen_at TIMESTAMP, material_updated_by BIGINT, material_updated_at TIMESTAMP)");
            sql.execute("INSERT INTO workorder(id,status) VALUES(1,'DRAFT')");
        }
        var config = new Configuration(new Environment("cache-fixture", new JdbcTransactionFactory(), source));
        config.addMapper(WorkorderMapper.class);
        var statement = config.getMappedStatement(WorkorderMapper.class.getName() + ".selectByPrimaryKeyForUpdate");
        assertTrue(statement.isFlushCacheRequired());
        assertFalse(statement.isUseCache());
        try (var session = new SqlSessionFactoryBuilder().build(config).openSession(false)) {
            var mapper = session.getMapper(WorkorderMapper.class);
            assertEquals("DRAFT", mapper.selectByPrimaryKey(1L).getStatus());
            try (var connection = source.getConnection(); var sql = connection.createStatement()) {
                sql.executeUpdate("UPDATE workorder SET status='RELEASED' WHERE id=1");
            }
            // Prove that an ordinary repeated SELECT would still return the cached object.
            assertEquals("DRAFT", mapper.selectByPrimaryKey(1L).getStatus());
            assertEquals("RELEASED", mapper.selectByPrimaryKeyForUpdate(1L).getStatus());
            session.rollback(true);
        }
    }
}
