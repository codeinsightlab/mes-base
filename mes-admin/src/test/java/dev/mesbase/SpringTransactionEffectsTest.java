package dev.mesbase;

import dev.mesbase.infrastructure.persistence.PersistenceOwner;
import dev.mesbase.infrastructure.persistence.PlatformPersistence;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Select;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.UnexpectedRollbackException;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** TEST ONLY H2 fixtures, exercising real Spring transactions. */
class SpringTransactionEffectsTest {
    @PersistenceOwner(PersistenceOwner.Kind.PLATFORM)
    public interface Probe {
        @Insert("INSERT INTO proof(id)VALUES(#{id})") int add(int id);
        @Select("SELECT COUNT(*) FROM proof") int count();
    }
    private PlatformPersistence platform() throws Exception {
        var data = new DriverManagerDataSource("jdbc:h2:mem:effects-"+UUID.randomUUID()+";DB_CLOSE_DELAY=-1", "sa", "");
        try(var connection=data.getConnection(); var statement=connection.createStatement()) { statement.execute("CREATE TABLE proof(id INT PRIMARY KEY)"); }
        return new PlatformPersistence(data, Set.of(Probe.class));
    }
    @Test void effectsSeeCommittedDataAndNestedWorkJoins() throws Exception {
        var platform=platform(); var calls=new ArrayList<String>();
        platform.run(() -> {
            platform.mapper(Probe.class).add(1);
            platform.run(() -> platform.mapper(Probe.class).add(2));
            platform.afterCommit(() -> { assertEquals(2, platform.mapper(Probe.class).count()); calls.add("committed"); });
            platform.afterRollback(() -> calls.add("rollback")); return null;
        });
        assertEquals(List.of("committed"),calls);
    }
    @Test void caughtNestedFailureMarksWholeTransactionRollbackOnly() throws Exception {
        var platform=platform(); var calls=new ArrayList<String>();
        assertThrows(UnexpectedRollbackException.class, () -> platform.run(() -> {
            platform.afterCommit(() -> calls.add("published"));
            platform.afterRollback(() -> calls.add("compensated"));
            try { platform.run(() -> { platform.mapper(Probe.class).add(1); throw new IllegalStateException("TEST ONLY"); }); }
            catch (IllegalStateException expected) { /* Outer continues; Spring still refuses commit. */ }
            return null;
        }));
        assertEquals(List.of("compensated"),calls);
        assertEquals(0,platform.mapper(Probe.class).count());
    }
}
