package dev.mesbase.server;

import dev.mesbase.infrastructure.persistence.ErpRoutingDataSource;
import dev.mesbase.infrastructure.persistence.ErpTransactions;
import dev.mesbase.infrastructure.persistence.OwnedTransactionManager;
import dev.mesbase.infrastructure.persistence.PersistenceFactories;
import dev.mesbase.infrastructure.persistence.PersistenceOwner;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.UnexpectedRollbackException;
import org.springframework.transaction.support.TransactionTemplate;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** TEST ONLY H2 markers, no production ERP schema or credentials. */
class ErpRoutingTest {
    @PersistenceOwner(PersistenceOwner.Kind.ERP)
    interface MarkerMapper {
        @Select("SELECT label FROM marker") String label();
        @Update("UPDATE marker SET label = #{label}") int rename(@Param("label") String label);
    }

    private DataSource pool(String label) throws Exception {
        var pool = new DriverManagerDataSource("jdbc:h2:mem:" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1", "sa", "");
        try (var connection = pool.getConnection(); var statement = connection.createStatement()) {
            statement.execute("CREATE TABLE marker(label VARCHAR(32))");
            try (var insert = connection.prepareStatement("INSERT INTO marker VALUES(?)")) {
                insert.setString(1, label);
                insert.executeUpdate();
            }
        }
        return pool;
    }

    private record Fixture(ErpRoutingDataSource source, OwnedTransactionManager manager,
                           ErpTransactions transactions, SqlSessionFactory factory, MarkerMapper mapper) implements AutoCloseable {
        @Override public void close() { source.close(); }
    }

    private Fixture fixture() throws Exception {
        var source = new ErpRoutingDataSource(Map.of("erp-main", pool("ERP"), "hr", pool("HR"), "wms", pool("WMS")));
        var manager = new OwnedTransactionManager(source);
        var factory = PersistenceFactories.create(source, Set.of(MarkerMapper.class), configuration -> {}, manager::verifyAccess);
        return new Fixture(source, manager, new ErpTransactions(manager), factory, new SqlSessionTemplate(factory).getMapper(MarkerMapper.class));
    }

    @Test void oneFactoryRoutesThreeIdentifiersAndNeverFallsBack() throws Exception {
        try (var f = fixture()) {
            for (int iteration = 0; iteration < 2; iteration++) {
                assertEquals("ERP", f.transactions.execute("erp-main", f.mapper::label));
                assertEquals("HR", f.transactions.execute("hr", f.mapper::label));
                assertEquals("WMS", f.transactions.execute("wms", f.mapper::label));
            }
            assertThrows(RuntimeException.class, f.mapper::label);
            assertThrows(RuntimeException.class, () -> f.transactions.execute("missing", f.mapper::label));
            assertThrows(RuntimeException.class, () -> f.transactions.execute(null, f.mapper::label));
            assertThrows(RuntimeException.class, () -> f.transactions.execute("../erp", f.mapper::label));
            assertEquals("HR", f.transactions.execute("hr", f.mapper::label));
        }
    }

    @Test void transactionPinsSourceAndRestoresItAfterRequiresNew() throws Exception {
        try (var f = fixture()) {
            f.transactions.execute("erp-main", () -> {
                assertThrows(RuntimeException.class, () -> f.transactions.execute("hr", f.mapper::label));
                assertEquals("ERP", f.transactions.execute("erp-main", f.mapper::label));
                var independent = new TransactionTemplate(f.manager);
                independent.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
                assertEquals("ERP", independent.execute(status -> f.mapper.label()));
                assertEquals("ERP", f.mapper.label());
                return null;
            });
            assertThrows(RuntimeException.class, f.mapper::label);
            assertEquals("WMS", f.transactions.execute("wms", f.mapper::label));
        }
    }

    @Test void springRollsBackFailuresAndJoinedTransactionsAndCleansSelection() throws Exception {
        try (var f = fixture()) {
            assertThrows(IllegalStateException.class, () -> f.transactions.execute("erp-main", () -> {
                f.mapper.rename("changed");
                throw new IllegalStateException("TEST ONLY failure");
            }));
            assertEquals("ERP", f.transactions.execute("erp-main", f.mapper::label));
            assertThrows(UnexpectedRollbackException.class, () -> f.transactions.execute("hr", () -> {
                assertThrows(IllegalStateException.class, () -> f.transactions.execute("hr", () -> {
                    f.mapper.rename("changed");
                    throw new IllegalStateException("TEST ONLY joined failure");
                }));
                return null;
            }));
            assertEquals("HR", f.transactions.execute("hr", f.mapper::label));
            assertThrows(RuntimeException.class, f.mapper::label);
        }
    }

    @Test void concurrentThreadsKeepIndependentSelections() throws Exception {
        try (var f = fixture(); var workers = Executors.newFixedThreadPool(2)) {
            var barrier = new CyclicBarrier(2);
            Callable<String> erp = () -> f.transactions.execute("erp-main", () -> {
                await(barrier);
                return f.mapper.label();
            });
            Callable<String> hr = () -> f.transactions.execute("hr", () -> {
                await(barrier);
                return f.mapper.label();
            });
            var erpResult = workers.submit(erp);
            var hrResult = workers.submit(hr);
            assertEquals("ERP", erpResult.get(10, TimeUnit.SECONDS));
            assertEquals("HR", hrResult.get(10, TimeUnit.SECONDS));
            assertThrows(RuntimeException.class, f.mapper::label);
        }
    }

    private static void await(CyclicBarrier barrier) {
        try { barrier.await(5, TimeUnit.SECONDS); }
        catch (Exception failure) { throw new IllegalStateException(failure); }
    }
}
