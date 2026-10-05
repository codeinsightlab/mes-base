package dev.mesbase.infrastructure.persistence;
import java.util.Objects;
import java.util.function.Function;
import dev.mesbase.core.factory.*;
import dev.mesbase.core.error.*;
import dev.mesbase.platform.execution.FactoryContext;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.transaction.support.*;
/** Dedicated database adapter; one selected connection for the entire transaction. */
public final class DedicatedDatabasePersistence implements BusinessPersistence {
    private record Active(BusinessRuntimeBinding binding,RuntimeRegistry.Lease lease,OwnedSqlSession session,
                          BusinessPersistenceResolver resolver,RuntimeRegistry registry) {
        void verify() {
            FactoryScope scope=FactoryContext.required();
            if(!scope.factoryId().equals(binding.factoryId()) || !binding.equals(resolver.resolve(scope))) throw new FoundationException(ErrorCode.CONFLICT);
            lease.verify(binding);
        }
    }
    private static final ThreadLocal<Active> ACTIVE=new ThreadLocal<>();
    private final BusinessPersistenceResolver resolver; private final RuntimeRegistry registry;
    public DedicatedDatabasePersistence(BusinessPersistenceResolver resolver,RuntimeRegistry registry) { this.resolver=resolver; this.registry=registry; }
    static void verifyStatement(DatasourceIdentity identity,long revision) {
        Active active=ACTIVE.get();
        if(active==null || !active.binding.datasourceIdentity().equals(identity) || active.binding.revision()!=revision) throw new FoundationException(ErrorCode.CONFLICT);
        active.verify();
    }
    public <T> T currentTransaction(Function<OwnedSqlSession,T> work) { return transaction(FactoryContext.required(),work); }
    public <T> T transaction(FactoryScope scope,Function<OwnedSqlSession,T> work) {
        Objects.requireNonNull(work);
        if(scope==null) throw new FoundationException(ErrorCode.FACTORY_SCOPE_REQUIRED);
        return FactoryContext.withScope(scope,() -> FactoryContext.pinned(scope,() -> execute(scope,work)));
    }
    private <T> T execute(FactoryScope scope,Function<OwnedSqlSession,T> work) {
        BusinessRuntimeBinding binding=resolver.resolve(scope); Active parent=ACTIVE.get();
        if(parent!=null) {
            parent.verify();
            if(parent.registry!=registry || parent.resolver!=resolver || !parent.binding.equals(binding)) throw new FoundationException(ErrorCode.CONFLICT);
            return new TransactionTemplate(parent.lease.runtime().transactions).execute(status -> work.apply(parent.session));
        }
        // No hidden participation in a platform/foreign transaction and no implicit distributed transaction.
        if(TransactionSynchronizationManager.isActualTransactionActive()) throw new FoundationException(ErrorCode.CONFLICT);
        try(RuntimeRegistry.Lease lease=registry.acquireDedicated(binding)) {
            RuntimeRegistry.Runtime runtime=lease.runtime();
            return new TransactionTemplate(runtime.transactions).execute(status -> {
                var connection=DataSourceUtils.getConnection(runtime.datasource);
                OwnedSqlSession session=new OwnedSqlSession(runtime.sessions.openSession(connection),runtime.mappers,() -> verifyStatement(binding.datasourceIdentity(),binding.revision()));
                Active active=new Active(binding,lease,session,resolver,registry); ACTIVE.set(active);
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override public void beforeCommit(boolean readOnly) { active.verify(); }
                });
                try { active.verify(); T result=work.apply(session); active.verify(); return result; }
                finally { try { session.close(); } finally { ACTIVE.remove(); } }
            });
        }
    }
}
