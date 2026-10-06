package dev.mesbase.infrastructure.persistence;

import com.github.pagehelper.PageHelper;
import java.sql.SQLException;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import javax.sql.DataSource;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.ExecutorType;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.defaults.DefaultSqlSession;
import org.apache.ibatis.transaction.jdbc.JdbcTransaction;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.transaction.support.TransactionTemplate;

/** Programmatic infrastructure/CLI operations share the standard Spring platform manager. */
public final class PlatformPersistence {
    private final Set<Class<?>> mappers;
    private final SqlSessionFactory sessions;
    private final SqlSessionTemplate template;
    private final OwnedTransactionManager transactions;
    private final SpringCommitActions effects = new SpringCommitActions();
    public PlatformPersistence(DataSource source, Set<Class<?>> mappers) { this(source, mappers, c -> {}); }
    public PlatformPersistence(DataSource source, Set<Class<?>> mappers, Consumer<Configuration> setup) {
        this.mappers = Set.copyOf(mappers);
        mappers.forEach(type -> {
            PersistenceOwner owner = type.getAnnotation(PersistenceOwner.class);
            if (owner == null || owner.value() != PersistenceOwner.Kind.PLATFORM)
                throw new IllegalArgumentException("Explicit PLATFORM mapper ownership required");
        });
        transactions = new OwnedTransactionManager(source);
        sessions = PersistenceFactories.create(source, mappers, setup, transactions::verifyAccess);
        template = new SqlSessionTemplate(sessions);
    }
    public PlatformPersistence(SqlSessionFactory sessions, OwnedTransactionManager transactions, Set<Class<?>> mappers) {
        this.sessions = sessions; this.transactions = transactions; this.mappers = Set.copyOf(mappers);
        template = new SqlSessionTemplate(sessions);
    }
    public SqlSessionFactory sessionFactory() { return sessions; }
    public OwnedTransactionManager transactionManager() { return transactions; }
    public <T> T mapper(Class<T> type) { return template.getMapper(type); }
    public <T> T run(Supplier<T> work) { return new TransactionTemplate(transactions).execute(status -> work.get()); }
    public <T> T transaction(Function<OwnedSqlSession, T> work) {
        return run(() -> owned(template, transactions::verifyAccess, work));
    }
    /** Deliberately independent metadata read, including while a business transaction is active. */
    public <T> T readOnly(Function<OwnedSqlSession, T> work) {
        // Separate short JDBC connection avoids joining or replacing the business transaction.
        var page = PageHelper.getLocalPage();
        PageHelper.clearPage();
        try (var connection = sessions.getConfiguration().getEnvironment().getDataSource().getConnection()) {
            connection.setReadOnly(true);
            try (var session = new DefaultSqlSession(sessions.getConfiguration(), sessions.getConfiguration().newExecutor(new JdbcTransaction(connection), ExecutorType.SIMPLE), true)) {
                // Metadata reads use the dedicated session but skip the write-unit guard.
                return PersistenceReadContext.independent(transactions, () -> owned(session, () -> {}, work));
            }
        } catch (SQLException failure) { throw new IllegalStateException("Platform metadata read failed", failure); }
        finally { PageHelper.clearPage(); if (page != null) PageHelper.setLocalPage(page); }
    }
    private <T> T owned(SqlSession session, Runnable verify, Function<OwnedSqlSession,T> work) {
        OwnedSqlSession owned = new OwnedSqlSession(session, mappers, verify);
        try { return work.apply(owned); } finally { owned.expire(); }
    }
    public void afterCommit(Runnable action) { effects.afterCommit(action); }
    public void afterRollback(Runnable action) { effects.afterRollback(action); }
}
