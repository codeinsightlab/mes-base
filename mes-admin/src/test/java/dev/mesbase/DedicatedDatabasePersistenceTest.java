package dev.mesbase;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import java.util.concurrent.*;
import dev.mesbase.core.factory.*;
import dev.mesbase.core.error.*;
import dev.mesbase.infrastructure.persistence.*;
import dev.mesbase.platform.execution.FactoryContext;
class DedicatedDatabasePersistenceTest {
    @AfterEach void clean() { FactoryContext.clear(); }
    @Test void caseASingleFactoryUsesSameAdapter() throws Exception { try(var f=new RuntimeFixture(1)) { assertEquals("A",f.read(RuntimeFixture.A));assertTrue(FactoryContext.current().isEmpty());assertThrows(FoundationException.class,() -> f.read(RuntimeFixture.B)); } }
    @Test void caseBMultipleFactoriesRemainIsolatedWithOverlappingIds() throws Exception {
        try(var f=new RuntimeFixture(2)) {
            assertEquals("A",f.read(RuntimeFixture.A));assertEquals("B",f.read(RuntimeFixture.B));
            f.persistence.transaction(RuntimeFixture.A,s -> s.mapper(RuntimeFixture.ProbeMapper.class).insert(new RuntimeFixture.Probe(2,"A-only")));
            assertEquals("A-only",f.persistence.transaction(RuntimeFixture.A,s -> s.mapper(RuntimeFixture.ProbeMapper.class).read(2)));
            assertNull(f.persistence.transaction(RuntimeFixture.B,s -> s.mapper(RuntimeFixture.ProbeMapper.class).read(2)));
        }
    }
    @Test void noScopeUnknownFactoryDisabledAndUnconfiguredFail() throws Exception {
        try(var f=new RuntimeFixture(2)) {
            assertEquals(ErrorCode.FACTORY_SCOPE_REQUIRED,assertThrows(FoundationException.class,() -> f.persistence.currentTransaction(s -> "never")).code());
            assertEquals(ErrorCode.FACTORY_NOT_FOUND,assertThrows(FoundationException.class,() -> f.read(new FactoryScope(new FactoryId("unknown")))).code());
            assertEquals(ErrorCode.FACTORY_DISABLED,assertThrows(FoundationException.class,() -> f.read(new FactoryScope(new FactoryId("C")))).code());
            assertEquals(ErrorCode.RUNTIME_NOT_CONFIGURED,assertThrows(FoundationException.class,() -> f.read(new FactoryScope(new FactoryId("D")))).code());
        }
    }
    @Test void transactionRejectsSwitchEvenBeforeNextSql() throws Exception {
        try(var f=new RuntimeFixture(2)) { f.persistence.transaction(RuntimeFixture.A,s -> {
            assertThrows(FoundationException.class,() -> FactoryContext.bind(RuntimeFixture.B));assertThrows(FoundationException.class,FactoryContext::clear);
            assertThrows(FoundationException.class,() -> f.read(RuntimeFixture.B));assertEquals("A",s.mapper(RuntimeFixture.ProbeMapper.class).read(1));return null;
        });assertTrue(FactoryContext.current().isEmpty()); }
    }
    @Test void nestedSameFactorySharesTransactionAndRollback() throws Exception {
        try(var f=new RuntimeFixture(2)) {
            assertThrows(IllegalStateException.class,() -> f.persistence.transaction(RuntimeFixture.A,s -> {
                f.persistence.transaction(RuntimeFixture.A,nested -> nested.mapper(RuntimeFixture.ProbeMapper.class).insert(new RuntimeFixture.Probe(2,"rollback")));throw new IllegalStateException("test only");
            }));assertNull(f.persistence.transaction(RuntimeFixture.A,s -> s.mapper(RuntimeFixture.ProbeMapper.class).read(2)));
        }
    }
    @Test void revisionMismatchFailsBeforeBorrowing() throws Exception {
        try(var f=new RuntimeFixture(2)) { f.bindings.put(RuntimeFixture.A.factoryId(),new BusinessRuntimeBinding(RuntimeFixture.A.factoryId(),new DatasourceIdentity("db-A"),2));f.resolver.replace(f.bindings);assertEquals(ErrorCode.CONFLICT,assertThrows(FoundationException.class,() -> f.read(RuntimeFixture.A)).code()); }
    }
    @Test void unknownDatasourceNeverFallsBack() throws Exception {
        try(var f=new RuntimeFixture(2)) { f.bindings.put(RuntimeFixture.A.factoryId(),new BusinessRuntimeBinding(RuntimeFixture.A.factoryId(),new DatasourceIdentity("not-registered"),1));f.resolver.replace(f.bindings);assertEquals(ErrorCode.RUNTIME_NOT_CONFIGURED,assertThrows(FoundationException.class,() -> f.read(RuntimeFixture.A)).code()); }
    }
    @Test void resolverChangeInsideTransactionRejectsAlreadyObtainedMapperAndRollsBack() throws Exception {
        try(var f=new RuntimeFixture(2)) {
            assertThrows(FoundationException.class,() -> f.persistence.transaction(RuntimeFixture.A,s -> {
                var mapper=s.mapper(RuntimeFixture.ProbeMapper.class);assertEquals("A",mapper.read(1));mapper.insert(new RuntimeFixture.Probe(2,"rollback"));
                f.bindings.put(RuntimeFixture.A.factoryId(),new BusinessRuntimeBinding(RuntimeFixture.A.factoryId(),new DatasourceIdentity("db-B"),1));f.resolver.replace(f.bindings);
                assertThrows(FoundationException.class,() -> mapper.read(1));return null;
            }));
            f.bindings.put(RuntimeFixture.A.factoryId(),new BusinessRuntimeBinding(RuntimeFixture.A.factoryId(),new DatasourceIdentity("db-A"),1));f.resolver.replace(f.bindings);
            assertNull(f.persistence.transaction(RuntimeFixture.A,s -> s.mapper(RuntimeFixture.ProbeMapper.class).read(2)));
        }
    }
    @Test void replacementDrainsOldPoolRejectsStaleWorkAndStartsNewRevision() throws Exception {
        try(var f=new RuntimeFixture(2)) {
            var old=f.pools.get("A");var next=RuntimeFixture.pool("replacement");RuntimeFixture.install(next,"new-A");
            assertThrows(FoundationException.class,() -> f.persistence.transaction(RuntimeFixture.A,s -> {
                var mapper=s.mapper(RuntimeFixture.ProbeMapper.class);mapper.read(1);
                f.registry.registerDedicated(RuntimeFixture.A.factoryId(),new DatasourceIdentity("db-A"),2,next,next::close,Set.of(RuntimeFixture.ProbeMapper.class));assertFalse(old.isClosed());
                assertThrows(FoundationException.class,() -> mapper.read(1));return null;
            }));assertTrue(old.isClosed());assertFalse(next.isClosed());
            f.bindings.put(RuntimeFixture.A.factoryId(),new BusinessRuntimeBinding(RuntimeFixture.A.factoryId(),new DatasourceIdentity("db-A"),2));f.resolver.replace(f.bindings);assertEquals("new-A",f.read(RuntimeFixture.A));
        }
    }
    @Test void mapperOwnershipIsExplicitNoNamespaceFallback() throws Exception {
        try(var f=new RuntimeFixture(2)) {
            f.persistence.transaction(RuntimeFixture.A,s -> { assertThrows(FoundationException.class,() -> s.mapper(RuntimeFixture.PlatformMapper.class));assertThrows(FoundationException.class,() -> s.mapper(RuntimeFixture.UnownedMapper.class));return null; });
            var wrong=RuntimeFixture.pool("wrong-owner");assertThrows(IllegalArgumentException.class,() -> f.registry.registerDedicated(RuntimeFixture.A.factoryId(),new DatasourceIdentity("wrong"),1,wrong,wrong::close,Set.of(RuntimeFixture.UnownedMapper.class)));assertTrue(wrong.isClosed());
        }
    }
    @Test void escapedMapperCannotExecuteOutsideUnitOfWork() throws Exception {
        try(var f=new RuntimeFixture(2)) { var mapper=f.persistence.transaction(RuntimeFixture.A,s -> s.mapper(RuntimeFixture.ProbeMapper.class));assertThrows(FoundationException.class,() -> mapper.read(1)); }
    }
    @Test void platformHasSeparateSessionAndTransactionManagerAndCrossTransactionIsRejected() throws Exception {
        try(var f=new RuntimeFixture(2);var pool=RuntimeFixture.pool("platform")) {
            RuntimeFixture.install(pool,"platform-only");var platform=new PlatformPersistence(pool,Set.of(RuntimeFixture.PlatformMapper.class));assertEquals("platform-only",platform.transaction(s -> s.mapper(RuntimeFixture.PlatformMapper.class).read(1)));
            platform.transaction(s -> { assertThrows(FoundationException.class,() -> f.read(RuntimeFixture.A));assertThrows(FoundationException.class,() -> s.mapper(RuntimeFixture.ProbeMapper.class));return null; });
            f.persistence.transaction(RuntimeFixture.A,s -> { assertThrows(FoundationException.class,() -> platform.transaction(p -> "never"));return null; });
        }
    }
    @Test void dedicatedAdapterCannotAccidentallyActAsSharedDatabase() throws Exception {
        try(var f=new RuntimeFixture(2)) {
            f.bindings.put(RuntimeFixture.B.factoryId(),new BusinessRuntimeBinding(RuntimeFixture.B.factoryId(),new DatasourceIdentity("db-A"),1));f.resolver.replace(f.bindings);assertThrows(FoundationException.class,() -> f.read(RuntimeFixture.B));
        }
    }
    @Test void parallelFactoriesUseThreadLocalTransactionsWithoutLeakingScopes() throws Exception {
        try(var f=new RuntimeFixture(2);var executor=Executors.newFixedThreadPool(2)) {
            var a=executor.submit(() -> { for(int i=0;i<10;i++) assertEquals("A",f.read(RuntimeFixture.A));assertTrue(FactoryContext.current().isEmpty()); });
            var b=executor.submit(() -> { for(int i=0;i<10;i++) assertEquals("B",f.read(RuntimeFixture.B));assertTrue(FactoryContext.current().isEmpty()); });a.get(10,TimeUnit.SECONDS);b.get(10,TimeUnit.SECONDS);
        }
    }
    @Test void staleRegistrationCannotDowngradeAndClosesRejectedPool() throws Exception {
        try(var f=new RuntimeFixture(2)) { var rejected=RuntimeFixture.pool("stale");assertThrows(FoundationException.class,() -> f.registry.registerDedicated(RuntimeFixture.A.factoryId(),new DatasourceIdentity("db-A"),1,rejected,rejected::close,Set.of(RuntimeFixture.ProbeMapper.class)));assertTrue(rejected.isClosed());assertEquals("A",f.read(RuntimeFixture.A)); }
    }
    @Test void ownedSessionsCannotEscapeToAnotherThreadEvenWithinActiveTransaction() throws Exception {
        try(var f=new RuntimeFixture(2);var pool=RuntimeFixture.pool("platform-thread-boundary");var executor=Executors.newSingleThreadExecutor()) {
            RuntimeFixture.install(pool,"platform-only");var platform=new PlatformPersistence(pool,Set.of(RuntimeFixture.PlatformMapper.class));
            platform.transaction(session -> {
                var mapper=session.mapper(RuntimeFixture.PlatformMapper.class);
                var attempted=executor.submit(() -> assertThrows(FoundationException.class,() -> mapper.read(1)));
                try { attempted.get(5,TimeUnit.SECONDS); } catch(Exception e) { throw new IllegalStateException(e); }
                assertEquals("platform-only",mapper.read(1));return null;
            });
        }
    }
    @Test void cannotRegisterAnAlreadyOwnedPoolOrCloseItOnRejectedRegistration() throws Exception {
        try(var f=new RuntimeFixture(2)) {
            var pool=f.pools.get("A");assertThrows(FoundationException.class,() -> f.registry.registerDedicated(RuntimeFixture.B.factoryId(),new DatasourceIdentity("db-A"),2,pool,pool::close,Set.of(RuntimeFixture.ProbeMapper.class)));
            assertFalse(pool.isClosed());assertEquals("A",f.read(RuntimeFixture.A));
        }
    }
    @Test void registryShutdownDrainsActiveLeaseAndRejectsNewWork() throws Exception {
        try(var f=new RuntimeFixture(2)) {
            var old=f.pools.get("A");var lease=f.registry.acquireDedicated(f.bindings.get(RuntimeFixture.A.factoryId()));
            f.registry.close();assertFalse(old.isClosed());assertThrows(FoundationException.class,() -> f.read(RuntimeFixture.A));lease.close();assertTrue(old.isClosed());lease.close();
        }
    }
    @Test void callbackChangingRevisionWithoutMoreSqlStillCannotCommit() throws Exception {
        try(var f=new RuntimeFixture(2)) {
            assertThrows(FoundationException.class,() -> f.persistence.transaction(RuntimeFixture.A,s -> {
                s.mapper(RuntimeFixture.ProbeMapper.class).insert(new RuntimeFixture.Probe(2,"rollback"));
                f.bindings.put(RuntimeFixture.A.factoryId(),new BusinessRuntimeBinding(RuntimeFixture.A.factoryId(),new DatasourceIdentity("db-A"),2));f.resolver.replace(f.bindings);return null;
            }));f.bindings.put(RuntimeFixture.A.factoryId(),new BusinessRuntimeBinding(RuntimeFixture.A.factoryId(),new DatasourceIdentity("db-A"),1));f.resolver.replace(f.bindings);
            assertNull(f.persistence.transaction(RuntimeFixture.A,s -> s.mapper(RuntimeFixture.ProbeMapper.class).read(2)));
        }
    }
    @Test void jdbcFailureRollsBackAndNextScopeStillWorks() throws Exception {
        try(var f=new RuntimeFixture(2)) {
            assertThrows(RuntimeException.class,() -> f.persistence.transaction(RuntimeFixture.A,s -> { var mapper=s.mapper(RuntimeFixture.ProbeMapper.class);mapper.insert(new RuntimeFixture.Probe(2,"rollback"));return mapper.insert(new RuntimeFixture.Probe(1,"duplicate")); }));
            assertNull(f.persistence.transaction(RuntimeFixture.A,s -> s.mapper(RuntimeFixture.ProbeMapper.class).read(2)));assertEquals("B",f.read(RuntimeFixture.B));assertTrue(FactoryContext.current().isEmpty());
        }
    }
}
