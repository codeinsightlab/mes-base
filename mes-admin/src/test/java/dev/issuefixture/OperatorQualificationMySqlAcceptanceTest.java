package dev.issuefixture;

import com.github.pagehelper.PageInterceptor;
import dev.mesbase.acceptance.AcceptanceBusinessDatabase;
import dev.mesbase.business.production.dao.MesOperationUserMapper;
import dev.mesbase.business.production.dao.MesOperatorQualificationMapper;
import dev.mesbase.business.production.dao.MesProductionExecutionMapper;
import dev.mesbase.business.production.dao.OperationMapper;
import dev.mesbase.business.production.dao.OperatorQualificationExtraMapper;
import dev.mesbase.business.production.dao.RoutingNodeMapper;
import dev.mesbase.business.production.dao.WorkorderMapper;
import dev.mesbase.business.production.domain.MesOperatorQualificationEntity;
import dev.mesbase.business.production.domain.MesOperatorQualificationExample;
import dev.mesbase.business.production.domain.MesOperationUserExample;
import dev.mesbase.business.production.domain.OperationEntity;
import dev.mesbase.business.production.domain.OperationExample;
import dev.mesbase.business.production.service.OperationUserDirectory;
import dev.mesbase.business.production.service.OperatorQualificationRules;
import dev.mesbase.business.production.service.ProductionChainLocks;
import dev.mesbase.business.wm.dao.WmStockDocumentMapper;
import dev.mesbase.common.core.domain.entity.SysMenu;
import dev.mesbase.core.error.ErrorCode;
import dev.mesbase.core.error.FoundationException;
import dev.mesbase.core.factory.FactoryId;
import dev.mesbase.core.factory.FactoryScope;
import dev.mesbase.infrastructure.persistence.PersistenceFactories;
import dev.mesbase.infrastructure.persistence.PlatformPersistence;
import dev.mesbase.platform.execution.FactoryContext;
import dev.mesbase.system.mapper.SysMenuMapper;
import dev.mesbase.system.production.OperationUserDirectoryMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.yaml.snakeyaml.Yaml;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Opt-in acceptance pinned to the user's designated TEST_DEV_PERF_20261019 business database. */
class OperatorQualificationMySqlAcceptanceTest {
    private static final String FACTORY = "TEST_DEV_PERF_20261019";
    private static final int DATASOURCE_ID = 9;
    private static final String DATABASE = "mes_base_dev_perf_test";
    private static final Path RECEIPT = Path.of(".local/knowledge/operator-all-qualification/v89-pre-migration-receipt.txt");

    private record Sources(DriverManagerDataSource business, DriverManagerDataSource center,
                           PlatformPersistence centerPersistence, JdbcTemplate businessJdbc) {
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "MES_OPERATOR_ALL_PREFLIGHT", matches = "true")
    void preflightAndSaveAffectedScopeSnapshot() throws Exception {
        var sources = sources();
        var flyway = flyway(sources.business(), true);
        flyway.validate();
        var before = snapshot(sources.businessJdbc());
        assertEquals(DATABASE, before.database());
        assertFalse(before.v89Applied(), "V89 must not already be recorded in the pre-migration receipt");
        assertFalse(before.tableExists(), "An untracked table-name collision must be reviewed before migration");
        assertTrue(before.pendingVersions().equals(List.of("89")),
                "The approved upgrade target must have V89 as its only pending version: " + before.pendingVersions());
        writeReceipt(before);
        System.out.println("V89_PREFLIGHT=PASS factory=" + FACTORY + " datasource=" + DATASOURCE_ID
                + " database=" + DATABASE + " priorVersion=" + before.currentVersion()
                + " pending=" + before.pendingVersions() + " backup=" + RECEIPT);
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "MES_OPERATOR_ALL_MYSQL_ACCEPTANCE", matches = "true")
    void migrateV89AndVerifyRealMysqlQualificationWithRollback() throws Exception {
        var sources = sources();
        var flyway = flyway(sources.business(), true);
        flyway.validate();
        var before = snapshot(sources.businessJdbc());
        if (!before.v89Applied()) {
            assertEquals(DATABASE, before.database());
            assertFalse(before.tableExists(), "Refusing to adopt an untracked mes_operator_qualification table");
            assertTrue(before.pendingVersions().equals(List.of("89")),
                    "Refusing to run unexpected pending migrations: " + before.pendingVersions());
            writeReceipt(before); // Captures migration history and user 100's explicit rows before the DDL write.
            assertEquals(1, flyway.migrate().migrationsExecuted, "Only V89 may execute in this acceptance");
        }
        flyway.validate();
        verifySchema(sources.businessJdbc());
        assertEquals(before.adminExplicitRows(), adminExplicitRows(sources.businessJdbc()),
                "V89 must preserve user 100's existing explicit operation qualifications");
        verifyPreMigrationReceipt(adminExplicitRows(sources.businessJdbc()));
        assertEquals(1, sources.businessJdbc().queryForObject(
                "SELECT COUNT(*) FROM flyway_business_schema_history WHERE version='89' AND success=1", Integer.class));
        rollbackOnlyQualificationAcceptance(sources);
        System.out.println("V89_MYSQL_ACCEPTANCE=PASS factory=" + FACTORY + " datasource=" + DATASOURCE_ID
                + " database=" + DATABASE + " schema=verified qualification=verified transaction=rolled-back");
    }

    private static Sources sources() throws Exception {
        var business = AcceptanceBusinessDatabase.performanceTestSource();
        var db = new JdbcTemplate(business);
        assertEquals(DATABASE, db.queryForObject("SELECT DATABASE()", String.class));

        Map<?, ?> root = new Yaml().load(Files.readString(Path.of("mes-admin/src/main/resources/application-dev.yml")));
        Map<?, ?> mes = (Map<?, ?>) root.get("mes");
        Map<?, ?> platform = (Map<?, ?>) mes.get("platform");
        var center = new DriverManagerDataSource((String) platform.get("url"),
                (String) platform.get("username"), (String) platform.get("password"));
        var centerJdbc = new JdbcTemplate(center);
        assertEquals(1, centerJdbc.queryForObject("""
                SELECT COUNT(*) FROM mf_factory f
                JOIN mf_datasource d ON d.datasource_id=f.datasource_id
                WHERE f.factory_id=? AND f.status='ENABLED' AND f.runtime_mode='LOCAL'
                  AND d.status='ENABLED' AND d.datasource_id=? AND d.db_host='192.168.18.25'
                  AND d.db_port=3306 AND d.db_name=?
                """, Integer.class, FACTORY, DATASOURCE_ID, DATABASE));
        var persistence = new PlatformPersistence(center,
                Set.of(SysMenuMapper.class, OperationUserDirectoryMapper.class), config -> {
                    config.getTypeAliasRegistry().registerAlias("SysMenu", SysMenu.class);
                    PersistenceFactories.pagination(config);
                });
        return new Sources(business, center, persistence, db);
    }

    private static Flyway flyway(DriverManagerDataSource source) {
        return flyway(source, false);
    }

    private static Flyway flyway(DriverManagerDataSource source, boolean ignorePendingForPreflight) {
        var configuration = Flyway.configure()
                .dataSource(source)
                .locations("filesystem:" + Path.of("mes-admin/src/main/resources/db/business").toAbsolutePath())
                .table("flyway_business_schema_history")
                .cleanDisabled(true)
                .baselineOnMigrate(false)
                .outOfOrder(false)
                .validateOnMigrate(true)
                .validateMigrationNaming(true)
                .target("89");
        if (ignorePendingForPreflight) configuration.ignoreMigrationPatterns("*:pending");
        return configuration.load();
    }

    private record Snapshot(String database, boolean tableExists, boolean v89Applied,
                            String currentVersion, List<String> pendingVersions,
                            List<Map<String, Object>> historyRows,
                            List<Map<String, Object>> adminExplicitRows,
                            String explicitTableDdl) {
    }

    private static Snapshot snapshot(JdbcTemplate db) throws Exception {
        String database = db.queryForObject("SELECT DATABASE()", String.class);
        boolean tableExists = db.queryForObject("""
                SELECT COUNT(*) FROM information_schema.tables
                WHERE table_schema=DATABASE() AND table_name='mes_operator_qualification'
                """, Integer.class) > 0;
        var history = db.queryForList("""
                SELECT installed_rank,version,description,type,script,checksum,installed_by,installed_on,execution_time,success
                FROM flyway_business_schema_history ORDER BY installed_rank
                """);
        boolean applied = history.stream().anyMatch(row -> "89".equals(String.valueOf(row.get("version")))
                && Boolean.TRUE.equals(row.get("success")));
        var info = flyway(AcceptanceBusinessDatabase.performanceTestSource(), true).info();
        String current = info.current() == null || info.current().getVersion() == null
                ? "<none>" : info.current().getVersion().getVersion();
        List<String> pending = java.util.Arrays.stream(info.pending())
                .map(row -> row.getVersion() == null ? "<repeatable>" : row.getVersion().getVersion())
                .sorted().toList();
        return new Snapshot(database, tableExists, applied, current, pending, history,
                adminExplicitRows(db), db.queryForObject("SHOW CREATE TABLE mes_operation_user",
                        (rs, row) -> rs.getString(2)));
    }

    private static List<Map<String, Object>> adminExplicitRows(JdbcTemplate db) {
        return db.queryForList("""
                SELECT id,operation_id,user_id,remark,create_by,create_time
                FROM mes_operation_user WHERE user_id=100 ORDER BY id
                """);
    }

    private static void writeReceipt(Snapshot snapshot) throws Exception {
        Files.createDirectories(RECEIPT.getParent());
        String content = "factory=" + FACTORY + "\ndatasource=" + DATASOURCE_ID + "\ndatabase=" + snapshot.database()
                + "\ncurrentVersion=" + snapshot.currentVersion() + "\npending=" + snapshot.pendingVersions()
                + "\ntableExists=" + snapshot.tableExists() + "\nv89Applied=" + snapshot.v89Applied()
                + "\nflywayHistory=" + snapshot.historyRows()
                + "\nuser100ExplicitQualifications=" + snapshot.adminExplicitRows()
                + "\nmes_operation_user DDL=" + snapshot.explicitTableDdl() + "\n";
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(content.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        Files.writeString(RECEIPT, content + "sha256=" + HexFormat.of().formatHex(digest) + "\n");
    }

    private static void verifyPreMigrationReceipt(List<Map<String, Object>> actualAdminRows) throws Exception {
        String receipt = Files.readString(RECEIPT);
        int digestLine = receipt.lastIndexOf("sha256=");
        assertTrue(digestLine > 0, "Missing pre-migration snapshot digest");
        String body = receipt.substring(0, digestLine);
        String expectedDigest = receipt.substring(digestLine + "sha256=".length()).trim();
        String actualDigest = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(body.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        assertEquals(expectedDigest, actualDigest, "Pre-migration snapshot integrity");
        String savedRows = Files.readAllLines(RECEIPT).stream()
                .filter(line -> line.startsWith("user100ExplicitQualifications="))
                .findFirst().orElseThrow();
        assertEquals("user100ExplicitQualifications=" + actualAdminRows, savedRows,
                "The real V89 migration must preserve the pre-migration explicit authorization snapshot");
    }

    private static void verifySchema(JdbcTemplate db) {
        assertTrue(db.queryForObject("""
                SELECT COUNT(*) FROM information_schema.tables
                WHERE table_schema=DATABASE() AND table_name='mes_operator_qualification'
                  AND engine='InnoDB' AND table_comment LIKE '%全工序资格%'
                """, Integer.class) == 1);
        var columns = db.queryForList("""
                SELECT column_name,column_type,is_nullable,column_default,column_comment
                FROM information_schema.columns
                WHERE table_schema=DATABASE() AND table_name='mes_operator_qualification'
                ORDER BY ordinal_position
                """);
        assertEquals(List.of("user_id", "all_operations", "revision", "create_by", "create_time", "update_by", "update_time"),
                columns.stream().map(row -> String.valueOf(row.get("column_name"))).toList());
        assertEquals(List.of("bigint", "tinyint(1)", "int", "bigint", "datetime(6)", "bigint", "datetime(6)"),
                columns.stream().map(row -> String.valueOf(row.get("column_type"))).toList());
        assertEquals("NO", columns.get(0).get("is_nullable"));
        assertEquals("NO", columns.get(1).get("is_nullable"));
        assertEquals("0", String.valueOf(columns.get(1).get("column_default")));
        assertEquals(1, db.queryForObject("""
                SELECT COUNT(*) FROM information_schema.statistics
                WHERE table_schema=DATABASE() AND table_name='mes_operator_qualification'
                  AND index_name='PRIMARY' AND column_name='user_id'
                """, Integer.class));
        assertEquals(1, db.queryForObject("""
                SELECT COUNT(*) FROM information_schema.statistics
                WHERE table_schema=DATABASE() AND table_name='mes_operator_qualification'
                """, Integer.class), "Only the primary key is expected");
        assertEquals(0, db.queryForObject("SELECT COUNT(*) FROM mes_operator_qualification", Integer.class),
                "V89 must not seed or convert existing explicit relationships");
    }

    private static void rollbackOnlyQualificationAcceptance(Sources sources) throws Exception {
        Set<Class<?>> mappers = Set.of(OperationMapper.class, MesOperationUserMapper.class,
                MesOperatorQualificationMapper.class, OperatorQualificationExtraMapper.class,
                MesProductionExecutionMapper.class, RoutingNodeMapper.class, WorkorderMapper.class,
                WmStockDocumentMapper.class);
        var configuration = new Configuration(new Environment("V89 ROLLBACK ONLY", new JdbcTransactionFactory(), sources.business()));
        configuration.setCacheEnabled(false);
        PersistenceFactories.pagination(configuration);
        mappers.forEach(configuration::addMapper);
        SqlSessionFactory sessions = new SqlSessionFactoryBuilder().build(configuration);
        var directory = new OperationUserDirectory(sources.centerPersistence(),
                sources.centerPersistence().mapper(SysMenuMapper.class),
                sources.centerPersistence().mapper(OperationUserDirectoryMapper.class));
        String tag = "QA_ALL_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        FactoryContext.bind(new FactoryScope(new FactoryId(FACTORY)));
        long userId;
        long testOperationId;
        try (var session = sessions.openSession(false)) {
            try {
            var qualification = session.getMapper(MesOperatorQualificationMapper.class);
            var users = directory.page(null, 0, 100).items();
            var userIds = users.stream().map(user -> user.id()).toList();
            var existing = new MesOperatorQualificationExample();
            existing.createCriteria().andUserIdIn(userIds);
            var configured = qualification.selectByExample(existing).stream().map(MesOperatorQualificationEntity::getUserId).collect(java.util.stream.Collectors.toSet());
            userId = users.stream().map(user -> user.id()).filter(id -> !configured.contains(id)).findFirst()
                    .orElseThrow(() -> new IllegalStateException("No currently authorized user without a qualification setting"));

            var operationMapper = session.getMapper(OperationMapper.class);
            var active = new OperationExample();
            active.createCriteria().andStatusEqualTo("ENABLED");
            OperationEntity template = operationMapper.selectByExample(active).stream().findFirst()
                    .orElseThrow(() -> new IllegalStateException("No enabled operation for rollback-only acceptance"));
            var added = new OperationEntity();
            added.setCode(tag);
            added.setName("TEST ONLY ALL qualification");
            added.setDescription("Rollback-only V89 acceptance row");
            added.setStatus(template.getStatus());
            added.setSort(template.getSort());
            added.setRevision(1);
            assertEquals(1, operationMapper.insert(added));
            testOperationId = added.getId();

            var explicit = new MesOperationUserExample();
            explicit.createCriteria().andOperationIdEqualTo(testOperationId).andUserIdEqualTo(userId);
            assertEquals(0, session.getMapper(MesOperationUserMapper.class).countByExample(explicit));
            var row = new MesOperatorQualificationEntity();
            row.setUserId(userId);
            row.setAllOperations(true);
            row.setRevision(1);
            row.setCreateBy(userId);
            row.setCreateTime(LocalDateTime.now());
            row.setUpdateBy(userId);
            row.setUpdateTime(LocalDateTime.now());
            assertEquals(1, qualification.insert(row));

            var rules = new OperatorQualificationRules(operationMapper, session.getMapper(RoutingNodeMapper.class),
                    session.getMapper(MesProductionExecutionMapper.class), session.getMapper(MesOperationUserMapper.class),
                    directory, new ProductionChainLocks(session.getMapper(WorkorderMapper.class),
                    session.getMapper(MesProductionExecutionMapper.class), session.getMapper(WmStockDocumentMapper.class)), qualification);
            rules.requireCapability(testOperationId, userId);
            assertTrue(session.getMapper(OperatorQualificationExtraMapper.class)
                    .candidateUserIds(testOperationId, null).contains(userId));
            assertEquals(ErrorCode.OPERATOR_NOT_AUTHORIZED,
                    assertThrows(FoundationException.class, () -> rules.requireCapability(testOperationId, Long.MAX_VALUE)).code());

            var off = new MesOperatorQualificationEntity();
            off.setAllOperations(false);
            off.setRevision(2);
            off.setUpdateBy(userId);
            off.setUpdateTime(LocalDateTime.now());
            var revision = new MesOperatorQualificationExample();
            revision.createCriteria().andUserIdEqualTo(userId).andRevisionEqualTo(1);
            assertEquals(1, qualification.updateByExampleSelective(off, revision));
            assertEquals(ErrorCode.OPERATOR_CAPABILITY_REQUIRED,
                    assertThrows(FoundationException.class, () -> rules.requireCapability(testOperationId, userId)).code());
            } finally {
                session.rollback();
            }
        } finally {
            FactoryContext.clear();
        }
        try (var session = sessions.openSession()) {
            assertEquals(null, session.getMapper(OperationMapper.class).selectByPrimaryKey(testOperationId),
                    "The synthetic future operation must be rolled back");
            assertEquals(null, session.getMapper(MesOperatorQualificationMapper.class).selectByPrimaryKey(userId),
                    "The test ALL setting must be rolled back");
        }
    }
}
