package dev.mesbase;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.pagehelper.Page;
import com.github.pagehelper.PageInterceptor;
import dev.mesbase.core.error.ErrorCode;
import dev.mesbase.core.error.FoundationException;
import dev.mesbase.core.factory.Factory;
import dev.mesbase.core.factory.FactoryCatalog;
import dev.mesbase.core.factory.FactoryId;
import dev.mesbase.core.factory.FactoryScope;
import dev.mesbase.core.factory.FactoryStatus;
import dev.mesbase.framework.sync.SyncCoordinator;
import dev.mesbase.infrastructure.persistence.PlatformPersistence;
import dev.mesbase.infrastructure.persistence.RuntimeRegistry;
import dev.mesbase.server.DruidPools;
import dev.mesbase.server.managed.DatasourcePasswordCipher;
import dev.mesbase.server.managed.ManagedDatasourceService;
import dev.mesbase.server.RoutingCacheFixture;
import dev.mesbase.system.factory.RoutingMetadataMapper;
import dev.mesbase.system.factory.ManagedDatasourceMapper;
import dev.mesbase.system.factory.ManagedFactoryMapper;
import dev.mesbase.system.sync.MasterSyncMapper;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.io.FileSystemResource;

class ManagedRuntimeMySqlTest {
    static int passed;

    static void check(boolean value, String label) {
        if (!value) throw new AssertionError(label);
        passed++;
    }

    static Properties yaml(Path path) throws Exception {
        var p = new Properties();
        for (var src : new YamlPropertySourceLoader().load("private", new FileSystemResource(path))) {
            var e = (EnumerablePropertySource<?>) src;
            for (String k : e.getPropertyNames()) p.setProperty(k, String.valueOf(e.getProperty(k)));
        }
        return p;
    }

    @Test
    void mysqlLifecycleAndDistributedSync() throws Exception {
        String configPath = System.getProperty("mes.test.managed.config");
        Assumptions.assumeTrue(configPath != null, "Requires explicit isolated MySQL configuration");
        String factoryId = Objects.requireNonNull(System.getProperty("mes.test.managed.factory"), "Explicit test factory required");
        var config = yaml(Path.of(configPath));
        String database = URI.create(config.getProperty("mes.platform.url").substring(5)).getPath();
        if (!Set.of("/mes_base_dev", "/mes_base_prod").contains(database))
            throw new IllegalArgumentException("Dedicated Base test database required");
        String key = config.getProperty("mes.runtime.datasource-key");
        var settings = new DruidPools.Settings(0, 0, 3, 5000, 5000, 30000, 60000, 300000, 900000, "SELECT 1", true, false, false, "stat,wall");
        try (var platform = DruidPools.create(settings, "migration-platform", config.getProperty("mes.platform.url"), config.getProperty("mes.platform.username"), config.getProperty("mes.platform.password")); var registry = new RuntimeRegistry()) {
            var persistence = new PlatformPersistence(platform, Set.of(ManagedDatasourceMapper.class, ManagedFactoryMapper.class, RoutingMetadataMapper.class, MasterSyncMapper.class), c -> {
                var interceptor = new PageInterceptor();
                var pagination = new Properties();
                pagination.setProperty("helperDialect", "mysql");
                interceptor.setProperties(pagination);
                c.addInterceptor(interceptor);
            });
            var unit = persistence;
            var service = new ManagedDatasourceService(persistence, persistence.mapper(ManagedDatasourceMapper.class), persistence.mapper(ManagedFactoryMapper.class), new DatasourcePasswordCipher(key), registry, platform, settings, Set.of(), Path.of("target/validation/managed-ddl-" + factoryId), RoutingCacheFixture.cache(persistence));
            var catalog = new FactoryCatalog() {
                public Optional<Factory> find(FactoryId id) {
                    return service.find(id);
                }

                public List<Factory> enabled(int o, int l) {
                    return service.enabled(o, l);
                }
            };
            var factory = service.factory(factoryId);
            var source = service.detail(factory.datasourceId());
            String testDb = "test_only_" + UUID.randomUUID().toString().replace("-", "");
            var editable = service.save(new ManagedDatasourceService.DatasourceForm(null, null, "TEST ONLY connection registration", "127.0.0.1", 3306, testDb, "test-only", "test-only-password"), true);
            try {
                check("DISABLED".equals(editable.status()), "new source only registers, does not connect");
                var updated = service.save(new ManagedDatasourceService.DatasourceForm(editable.datasourceId(), editable.revision(), "TEST ONLY updated", "127.0.0.1", 3306, testDb, "test-only", null), false);
                check(updated.revision() == editable.revision() + 1, "optimistic source revision advances");
                try {
                    service.save(new ManagedDatasourceService.DatasourceForm(editable.datasourceId(), editable.revision(), "stale", "127.0.0.1", 3306, testDb, "test-only", null), false);
                    throw new AssertionError("stale source edited");
                } catch (FoundationException ex) {
                    check(ex.code() == ErrorCode.RESOURCE_CONFLICT, "stale source edit rejected");
                }
                String serialized = new ObjectMapper().writeValueAsString(updated);
                check(!serialized.contains("password") && !serialized.contains("Cipher"), "view does not expose credentials");
            } finally {
                service.delete(editable.datasourceId());
            }

            check(service.enabled(0, 100).size() == 1, "catalog database-backed");
            var sourcePage = service.pageSources(1, 20);
            check(sourcePage instanceof Page<?> && ((Page<?>) sourcePage).getTotal() == 1, "datasource native pagination has real total");
            var factoryPage = service.pageFactories(1, 20);
            check(factoryPage instanceof Page<?> && ((Page<?>) factoryPage).getTotal() == 1, "factory native pagination has real total");
            try {
                service.status(source.datasourceId(), false);
                throw new AssertionError("bound source disable accepted");
            } catch (FoundationException e) {
                check(e.code() == ErrorCode.RESOURCE_CONFLICT, "bound disable rejected");
            }
            var scope = new FactoryScope(new FactoryId(factoryId));
            var admission = service.begin(scope);
            var binding = admission.binding();
            admission.lease().close();
            check(registry.contains(binding), "runtime loaded lazily");
            check(service.ddl(source.datasourceId(), false).sql().contains("Tables:"), "DDL snapshot without records");
            var first = new SyncCoordinator(persistence, persistence.mapper(MasterSyncMapper.class), catalog);
            var second = new SyncCoordinator(persistence, persistence.mapper(MasterSyncMapper.class), catalog);
            String syncKey = "test.only.migration." + UUID.randomUUID();
            try {
                var pool = Executors.newFixedThreadPool(2);
                var gate = new CountDownLatch(1);
                var results = new ArrayList<Future<SyncCoordinator.Run>>();
                for (var c : List.of(first, second))
                    results.add(pool.submit(() -> {
                        gate.await();
                        try {
                            return c.claim(scope, syncKey, Duration.ofSeconds(60));
                        } catch (FoundationException e) {
                            if (e.code() != ErrorCode.SYNC_RUNNING) throw e;
                            return null;
                        }
                    }));
                gate.countDown();
                var runs = new ArrayList<SyncCoordinator.Run>();
                for (var result : results) {
                    var run = result.get(10, TimeUnit.SECONDS);
                    if (run != null) runs.add(run);
                }
                pool.shutdown();
                check(runs.size() == 1, "independent coordinators one claim");
                var old = runs.getFirst();
                first.progress(old, 3);
                first.succeed(old, LocalDateTime.now());
                var next = second.claim(scope, syncKey, Duration.ofSeconds(60));
                String otherId = "TEST_SYNC_" + UUID.randomUUID().toString().replace("-", "");
                service.saveFactory(new ManagedDatasourceService.FactoryForm(otherId, "test-only", otherId.substring(0, 32), "TEST ONLY sync isolation", null, "ENABLED", null), true);
                try {
                    var otherScope = new FactoryScope(new FactoryId(otherId));
                    var independent = second.claim(otherScope, syncKey, Duration.ofSeconds(60));
                    check(!independent.runToken()
                            .equals(next.runToken()), "same key in different factory does not block");
                    second.fail(independent);
                } finally {
                    try (var c = platform.getConnection(); var st = c.prepareStatement("DELETE FROM mes_master_sync WHERE factory_id=?")) {
                        st.setString(1, otherId);
                        st.executeUpdate();
                    }
                    service.deleteFactory(otherId, 1);
                }
                try {
                    first.progress(old, 9);
                    throw new AssertionError("old token accepted");
                } catch (FoundationException e) {
                    check(e.code() == ErrorCode.SYNC_EXECUTION_LOST, "old token refused");
                }
                try (var c = platform.getConnection(); var st = c.prepareStatement("UPDATE mes_master_sync SET updated_at=DATE_SUB(CURRENT_TIMESTAMP(6),INTERVAL 120 SECOND) WHERE factory_id=? AND sync_key=?")) {
                    st.setString(1, factoryId);
                    st.setString(2, syncKey);
                    st.executeUpdate();
                }
                var recovered = first.claim(scope, syncKey, Duration.ofSeconds(60));
                check(!next.runToken().equals(recovered.runToken()), "stale lease recovered with new token");
                try {
                    second.assertOwner(next);
                    throw new AssertionError("old worker retained authority");
                } catch (FoundationException e) {
                    check(e.code() == ErrorCode.SYNC_EXECUTION_LOST, "old worker stops");
                }
                first.fail(recovered);
                check(first.state(scope, syncKey).lastSuccessAt() != null, "failure preserves successful watermark");
                Path gateRoot = Files.createTempDirectory("mes-sync-process-");
                try {
                    var children = new ArrayList<Process>();
                    for (int i = 0; i < 2; i++)
                        children.add(new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java")
                                .toString(), "-cp", System.getProperty("java.class.path"), ManagedRuntimeMySqlTest.class.getName(), configPath, factoryId, syncKey, gateRoot.toString(), String.valueOf(i)).redirectErrorStream(true)
                                .redirectOutput(gateRoot.resolve("child-" + i + ".log").toFile()).start());
                    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
                    while ((!Files.exists(gateRoot.resolve("ready-0")) || !Files.exists(gateRoot.resolve("ready-1"))) && System.nanoTime() < deadline)
                        Thread.sleep(25);
                    check(Files.exists(gateRoot.resolve("ready-0")) && Files.exists(gateRoot.resolve("ready-1")), "two JVMs ready");
                    Files.createFile(gateRoot.resolve("go"));
                    for (var child : children)
                        check(child.waitFor(15, TimeUnit.SECONDS) && child.exitValue() == 0, "independent JVM probe exits successfully");
                    String logs = Files.readString(gateRoot.resolve("child-0.log")) + Files.readString(gateRoot.resolve("child-1.log"));
                    check(logs.lines().filter(l -> l.equals("PROBE_ACQUIRED")).count() == 1 && logs.lines()
                            .filter(l -> l.equals("PROBE_BUSY")).count() == 1, "two JVMs one database claim");
                    var acquired = first.state(scope, syncKey);
                    first.fail(new SyncCoordinator.Run(scope, syncKey, acquired.runToken()));
                } finally {
                    try (var files = Files.list(gateRoot)) {
                        for (var file : files.toList()) Files.deleteIfExists(file);
                    }
                    Files.deleteIfExists(gateRoot);
                }
            } finally {
                try (var c = platform.getConnection(); var st = c.prepareStatement("DELETE FROM mes_master_sync WHERE factory_id=? AND sync_key=?")) {
                    st.setString(1, factoryId);
                    st.setString(2, syncKey);
                    st.executeUpdate();
                }
            }
            System.out.println("management/sync real MySQL checks passed=" + passed + " database=" + database);
        }
    }

    public static void main(String[] args) throws Exception {
        var config = yaml(Path.of(args[0]));
        var settings = new DruidPools.Settings(0, 0, 2, 5000, 5000, 30000, 60000, 300000, 900000, "SELECT 1", true, false, false, "stat,wall");
        try (var ds = DruidPools.create(settings, "sync-process-probe", config.getProperty("mes.platform.url"), config.getProperty("mes.platform.username"), config.getProperty("mes.platform.password"))) {
            var p = new PlatformPersistence(ds, Set.of(ManagedFactoryMapper.class, RoutingMetadataMapper.class, MasterSyncMapper.class));
            FactoryCatalog catalog = new FactoryCatalog() {
                public Optional<Factory> find(FactoryId id) {
                    return p.readOnly(s -> Optional.ofNullable(s.mapper(ManagedFactoryMapper.class).find(id.value())))
                            .map(f -> new Factory(new FactoryId(f.factoryId()), f.enterpriseId(), f.factoryCode(), f.factoryName(), FactoryStatus.valueOf(f.status())));
                }

                public List<Factory> enabled(int offset, int limit) {
                    throw new UnsupportedOperationException("Not used by probe");
                }
            };
            var coordinator = new SyncCoordinator(p, p.mapper(MasterSyncMapper.class), catalog);
            Path gate = Path.of(args[3]);
            Files.createFile(gate.resolve("ready-" + args[4]));
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
            while (!Files.exists(gate.resolve("go")) && System.nanoTime() < deadline) Thread.sleep(25);
            if (!Files.exists(gate.resolve("go"))) throw new IllegalStateException("Probe gate timeout");
            try {
                coordinator.claim(new FactoryScope(new FactoryId(args[1])), args[2], Duration.ofSeconds(60));
                System.out.println("PROBE_ACQUIRED");
            } catch (FoundationException e) {
                if (e.code() != ErrorCode.SYNC_RUNNING) throw e;
                System.out.println("PROBE_BUSY");
            }
        }
    }

}
