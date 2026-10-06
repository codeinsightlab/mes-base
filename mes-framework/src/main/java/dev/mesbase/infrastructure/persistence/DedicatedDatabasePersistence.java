package dev.mesbase.infrastructure.persistence;

import dev.mesbase.core.error.ErrorCode;
import dev.mesbase.core.error.FoundationException;
import dev.mesbase.core.factory.FactoryScope;
import dev.mesbase.platform.execution.FactoryContext;
import java.util.Set;
import java.util.function.Function;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.transaction.support.TransactionTemplate;

/** Compatibility callback for infrastructure/codegen; transaction semantics belong to Spring. */
public final class DedicatedDatabasePersistence implements BusinessPersistence {
    private final SqlSessionTemplate template;
    private final OwnedTransactionManager transactions;
    private final Set<Class<?>> mappers;
    public DedicatedDatabasePersistence(BusinessPersistenceResolver resolver, RuntimeRegistry registry) {
        mappers = registry.mapperTypes();
        FactoryBusinessDataSource source = new FactoryBusinessDataSource(resolver, registry);
        transactions = new OwnedTransactionManager(source);
        template = new SqlSessionTemplate(PersistenceFactories.create(source, mappers, PersistenceFactories::pagination, transactions::verifyAccess));
    }
    public DedicatedDatabasePersistence(SqlSessionFactory factory, OwnedTransactionManager transactions, Set<Class<?>> mappers) {
        template = new SqlSessionTemplate(factory); this.transactions = transactions; this.mappers = Set.copyOf(mappers);
    }
    public <T> T currentTransaction(Function<OwnedSqlSession,T> work) { return transaction(FactoryContext.required(), work); }
    public <T> T transaction(FactoryScope scope, Function<OwnedSqlSession,T> work) {
        if (scope == null) throw new FoundationException(ErrorCode.FACTORY_SCOPE_REQUIRED);
        return FactoryContext.withScope(scope, () -> FactoryContext.pinned(scope, () ->
            new TransactionTemplate(transactions).execute(status -> {
                OwnedSqlSession session = new OwnedSqlSession(template, mappers, transactions::verifyAccess);
                try { return work.apply(session); } finally { session.expire(); }
            })));
    }
}
