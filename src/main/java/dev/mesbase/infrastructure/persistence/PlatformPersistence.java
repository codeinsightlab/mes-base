package dev.mesbase.infrastructure.persistence;
import javax.sql.DataSource;
import java.util.Set;
import java.util.Properties;
import java.util.function.Function;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.*;
import org.apache.ibatis.transaction.managed.ManagedTransactionFactory;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.jdbc.support.JdbcTransactionManager;
import org.springframework.transaction.support.*;
import dev.mesbase.core.error.*;
/** Separate PLATFORM SqlSessionFactory and local transaction manager. No business fallback. */
public final class PlatformPersistence {
    private final DataSource datasource; private final Set<Class<?>> mappers;
    private final SqlSessionFactory sessions; private final JdbcTransactionManager transactions;
    public PlatformPersistence(DataSource datasource,Set<Class<?>> mappers) {
        this.datasource=datasource; this.mappers=Set.copyOf(mappers);
        ManagedTransactionFactory factory=new ManagedTransactionFactory(); Properties p=new Properties();p.setProperty("closeConnection","false");factory.setProperties(p);
        Configuration config=new Configuration(new Environment("platform",factory,datasource)); config.setCacheEnabled(false); config.setLocalCacheScope(LocalCacheScope.STATEMENT);
        for(Class<?> mapper:mappers) {
            var owner=mapper.getAnnotation(PersistenceOwner.class);
            if(owner==null || owner.value()!=PersistenceOwner.Kind.PLATFORM) throw new IllegalArgumentException("Explicit PLATFORM mapper ownership required");
            config.addMapper(mapper);
        }
        sessions=new SqlSessionFactoryBuilder().build(config); transactions=new JdbcTransactionManager(datasource);
    }
    public <T> T transaction(Function<OwnedSqlSession,T> work) {
        if(TransactionSynchronizationManager.isActualTransactionActive()) throw new FoundationException(ErrorCode.CONFLICT);
        return new TransactionTemplate(transactions).execute(status -> {
            var session=new OwnedSqlSession(sessions.openSession(DataSourceUtils.getConnection(datasource)),mappers,() -> {});
            try { return work.apply(session); } finally { session.close(); }
        });
    }
}
