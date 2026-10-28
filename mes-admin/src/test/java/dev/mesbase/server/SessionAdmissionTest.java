package dev.mesbase.server;

import dev.mesbase.common.core.domain.entity.SysUser;
import dev.mesbase.common.core.domain.model.LoginUser;
import dev.mesbase.common.core.redis.UserSessionStore;
import dev.mesbase.core.error.FoundationException;
import dev.mesbase.framework.web.service.TokenService;
import dev.mesbase.infrastructure.persistence.PlatformPersistence;
import dev.mesbase.system.mapper.SessionAccountMapper;
import dev.mesbase.system.mapper.SysUserMapper;
import dev.mesbase.system.mapper.SysUserRoleMapper;
import dev.mesbase.system.mapper.SysUserPostMapper;
import dev.mesbase.system.service.impl.SysUserServiceImpl;
import org.springframework.test.util.ReflectionTestUtils;
import dev.mesbase.system.service.SessionRevocationService;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.sql.Connection;
import org.apache.ibatis.executor.statement.StatementHandler;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Signature;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.never;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * TEST ONLY: actual compact mapper/outbox SQL against isolated H2; no real account data.
 */
class SessionAdmissionTest {
    /**
     * H2 lacks MySQL SYSDATE(); only the test clock expression is adapted, not predicates/version writes.
     */
    @Intercepts(@Signature(type = StatementHandler.class, method = "prepare", args = {Connection.class, Integer.class}))
    static class TestClock implements Interceptor {
        public Object intercept(Invocation invocation) throws Throwable {
            var sql = ((StatementHandler) invocation.getTarget()).getBoundSql();
            ReflectionTestUtils.setField(sql, "sql", sql.getSql().replace("sysdate()", "CURRENT_TIMESTAMP"));
            return invocation.proceed();
        }
    }

    private record Fixture(PlatformPersistence platform, JdbcTemplate jdbc, SessionAccountMapper accounts) {
    }

    private Fixture fixture() throws Exception {
        var source = new DriverManagerDataSource("jdbc:h2:mem:" + UUID.randomUUID()
                + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        var jdbc = new JdbcTemplate(source);
        jdbc.execute("CREATE TABLE sys_user(user_id BIGINT PRIMARY KEY,user_name VARCHAR(30),dept_id BIGINT,"
                + "auth_revision BIGINT,status VARCHAR(1),del_flag VARCHAR(1),nick_name VARCHAR(30),avatar VARCHAR(100),"
                + "user_type VARCHAR(2),password VARCHAR(100),update_time TIMESTAMP)");
        jdbc.execute("CREATE TABLE sys_role(role_id BIGINT,role_name VARCHAR(30),role_key VARCHAR(30),role_sort INT,"
                + "data_scope VARCHAR(1),kind VARCHAR(10),factory_id VARCHAR(30),status VARCHAR(1),del_flag VARCHAR(1))");
        jdbc.execute("CREATE TABLE sys_user_role(user_id BIGINT,role_id BIGINT)");
        jdbc.execute(Files.readString(Path.of("mes-admin/src/main/resources/db/system/V19__indexed_session_revocation.sql")));
        for (String sql : Files.readString(Path.of("mes-admin/src/main/resources/db/system/V20__session_revocation_retry_schedule.sql"))
                .split(";")) {
            if (!sql.isBlank()) jdbc.execute(sql);
        }
        jdbc.execute("INSERT INTO sys_user VALUES(42,'fixture',7,1,'0','0','Fixture','', '00','TEST ONLY secret',NULL)");
        jdbc.execute("INSERT INTO sys_role VALUES(8,'Factory','fixture',1,'4','FACTORY','A','0','0')");
        jdbc.execute("INSERT INTO sys_user_role VALUES(42,8)");
        var platform = new PlatformPersistence(source, Set.of(SysUserMapper.class, SessionAccountMapper.class), c -> {
            c.getTypeAliasRegistry().registerAliases("dev.mesbase.common.core.domain.entity");
            c.addInterceptor(new TestClock());
        });
        return new Fixture(platform, jdbc, platform.mapper(SessionAccountMapper.class));
    }

    @Test
    void safeIdentityRestoresCurrentDepartmentRolesAndRejectsLaterCredentialChanges() throws Exception {
        var f = fixture();
        var redis = mock(UserSessionStore.class);
        var payload = new AtomicReference<String>();
        when(redis.issue(anyString(), anyString(), anyLong(), anyLong(), anyString(), any(Instant.class))).thenAnswer(call -> {
            payload.set(call.getArgument(4));
            return true;
        });
        when(redis.read(anyString(), any())).thenAnswer(call -> payload.get());
        var tokens = new TokenService(redis, f.accounts(), new MockEnvironment());
        SysUser account = f.accounts().account(42);
        assertNull(account.getPassword());
        assertEquals("4", account.getRoles().getFirst().getDataScope());
        String raw = tokens.createToken(new LoginUser(42L, 7L, account, Set.of()), new MockHttpServletRequest());
        assertTrue(raw.matches("[A-Fa-f0-9-]{36}\\.[A-Za-z0-9_-]{43}"));
        String sessionId = raw.substring(0, raw.indexOf('.'));
        assertThrows(FoundationException.class, () -> tokens.resolve(sessionId));
        assertFalse(payload.get().contains("password"));
        assertFalse(payload.get().contains("roles"));
        assertFalse(payload.get().contains("TEST ONLY secret"));
        LoginUser admitted = tokens.resolve(raw);
        assertEquals(7L, admitted.getDeptId());
        f.jdbc().update("UPDATE sys_user SET status='1',auth_revision=2 WHERE user_id=42");
        assertThrows(FoundationException.class, () -> tokens.resolve(raw));
        // Already admitted request retains its own identity rather than re-reading management state.
        assertEquals("0", admitted.getUser().getStatus());
        f.jdbc().update("UPDATE sys_user SET status='0',auth_revision=3 WHERE user_id=42");
        assertThrows(FoundationException.class, () -> tokens.resolve(raw));
        f.jdbc().update("UPDATE sys_user SET auth_revision=1,del_flag='2' WHERE user_id=42");
        assertThrows(FoundationException.class, () -> tokens.resolve(raw));
        f.jdbc().update("UPDATE sys_user SET del_flag='0',dept_id=9 WHERE user_id=42");
        f.jdbc().update("UPDATE sys_role SET status='1' WHERE role_id=8");
        assertEquals(9L, tokens.resolve(raw).getDeptId());
        assertEquals(List.of(), tokens.resolve(raw).getUser().getRoles());
        tokens.delLoginUser(sessionId);
        verify(redis).logout(sessionId);
    }

    @Test
    void actualUserMutationEntrypointsIncrementFenceAndQueueRevocation() throws Exception {
        var f = fixture();
        var redis = mock(UserSessionStore.class);
        var revocations = new SessionRevocationService(f.accounts(), redis, f.platform().transactionManager());
        var users = new SysUserServiceImpl();
        ReflectionTestUtils.setField(users, "userMapper", f.platform().mapper(SysUserMapper.class));
        ReflectionTestUtils.setField(users, "userRoleMapper", mock(SysUserRoleMapper.class));
        ReflectionTestUtils.setField(users, "userPostMapper", mock(SysUserPostMapper.class));
        ReflectionTestUtils.setField(users, "revocations", revocations);
        var change = new SysUser();
        change.setUserId(42L);
        change.setStatus("1");
        f.platform().run(() -> users.updateUserStatus(change));
        assertEquals(2, f.accounts().revision(42));
        verify(redis).revoke(42, 2);
        change.setStatus(null);
        change.setPassword("TEST ONLY changed hash");
        f.platform().run(() -> users.resetPwd(change));
        assertEquals(3, f.accounts().revision(42));
        verify(redis).revoke(42, 3);
        f.platform().run(() -> users.deleteUserById(42L));
        assertEquals(4, f.accounts().revision(42));
        assertEquals("2", f.accounts().account(42).getDelFlag());
        verify(redis).revoke(42, 4);
        assertEquals(4, f.accounts().pending().getFirst().revision());
    }

    @Test
    void revocationIsCommittedWithAccountAndRedisFailureRemainsRecoverable() throws Exception {
        var f = fixture();
        var redis = mock(UserSessionStore.class);
        var revocations = new SessionRevocationService(f.accounts(), redis, f.platform().transactionManager());
        assertThrows(IllegalStateException.class, () -> f.platform().run(() -> {
            f.jdbc().update("UPDATE sys_user SET auth_revision=2 WHERE user_id=42");
            revocations.enqueue(List.of(42L));
            throw new IllegalStateException("TEST ONLY rollback");
        }));
        assertEquals(1, f.accounts().revision(42));
        assertEquals(List.of(), f.accounts().pending());
        doThrow(new IllegalStateException("TEST ONLY Redis unavailable")).when(redis).revoke(42, 2);
        assertThrows(FoundationException.class, () -> f.platform().run(() -> {
            f.jdbc().update("UPDATE sys_user SET auth_revision=2 WHERE user_id=42");
            revocations.enqueue(List.of(42L));
            return null;
        }));
        assertEquals(2, f.accounts().revision(42));
        assertEquals(2, f.accounts().pending().getFirst().revision());
        revocations.retryPending();
        assertEquals(1, f.jdbc().queryForObject("SELECT attempts FROM sys_session_revocation", Integer.class));
        reset(redis);
        f.jdbc().update("UPDATE sys_session_revocation SET next_attempt_at=CURRENT_TIMESTAMP");
        revocations.retryPending();
        assertEquals(List.of(), f.accounts().pending());
        verify(redis).revoke(42, 2);
        f.platform().run(() -> {
            f.jdbc().update("UPDATE sys_user SET auth_revision=3 WHERE user_id=42");
            revocations.enqueue(List.of(42L));
            return null;
        });
        assertEquals(0, f.accounts().delivered(42, 2));
        assertNotNull(f.accounts().pending().getFirst());
    }

    @Test
    void boundedFairRetriesAdvancePastPersistentFailuresAndNewRevisionResetsBackoff() throws Exception {
        var f = fixture();
        var redis = mock(UserSessionStore.class);
        var revocations = new SessionRevocationService(f.accounts(), redis, f.platform().transactionManager());
        for (long id = 100; id < 350; id++) {
            f.jdbc().update("INSERT INTO sys_session_revocation(user_id,revision) VALUES(?,1)", id);
            if (id < 200) doThrow(new IllegalStateException("TEST ONLY persistent failure")).when(redis).revoke(id, 1);
        }
        revocations.retryPending();
        assertEquals(100, f.accounts().pending().size());
        assertEquals(200, f.accounts().pending().getFirst().userId());
        revocations.retryPending();
        revocations.retryPending();
        assertEquals(100, f.jdbc().queryForObject("SELECT COUNT(*) FROM sys_session_revocation", Integer.class));
        assertEquals(List.of(), f.accounts().pending());
        f.jdbc().update("INSERT INTO sys_user(user_id,auth_revision) VALUES(100,2)");
        f.jdbc().update("UPDATE sys_session_revocation SET attempts=20,next_attempt_at=? WHERE user_id=100",
                Timestamp.from(Instant.now().plusSeconds(300)));
        f.platform().run(() -> {
            revocations.enqueue(List.of(100L));
            return null;
        });
        var latest = f.accounts().pending().getFirst();
        assertEquals(2, latest.revision());
        assertEquals(0, latest.attempts());
        assertEquals(0, f.accounts().delivered(100, 1));
        assertEquals(0, f.accounts().failed(100, 1, 300));
        assertEquals(0, f.accounts().pending().getFirst().attempts());
        // Two consumers can hold the same batch; delivery is idempotent and acknowledgement is conditional.
        var second = new SessionRevocationService(f.accounts(), redis, f.platform().transactionManager());
        var held = f.accounts().pending().getFirst();
        second.retryPending();
        redis.revoke(held.userId(), held.revision());
        assertEquals(0, f.accounts().delivered(held.userId(), held.revision()));
    }

    @Test
    void batchVersionsAreBoundedDeduplicatedAndMissingAccountsRollback() throws Exception {
        var f = fixture();
        var accounts = spy(f.accounts());
        var redis = mock(UserSessionStore.class);
        var revocations = new SessionRevocationService(accounts, redis, f.platform().transactionManager());
        var ids = new ArrayList<Long>();
        for (long id = 100; id < 1101; id++) {
            f.jdbc().update("INSERT INTO sys_user(user_id,auth_revision) VALUES(?,1)", id);
            ids.add(id);
        }
        ids.add(100L);
        f.platform().run(() -> {
            revocations.enqueue(ids);
            return null;
        });
        verify(accounts, times(3)).revisions(anyList());
        verify(accounts, never()).revision(anyLong());
        assertEquals(1001, f.jdbc().queryForObject("SELECT COUNT(*) FROM sys_session_revocation", Integer.class));
        verify(redis, times(1001)).revoke(anyLong(), anyLong());
        reset(redis);
        f.platform().run(() -> {
            revocations.enqueue(List.of());
            return null;
        });
        verifyNoInteractions(redis);
        assertThrows(IllegalArgumentException.class, () -> f.platform().run(() -> {
            revocations.enqueue(null);
            return null;
        }));
        assertThrows(IllegalArgumentException.class, () -> f.platform().run(() -> {
            revocations.enqueue(Arrays.asList(42L, null));
            return null;
        }));
        assertThrows(IllegalArgumentException.class, () -> f.platform().run(() -> {
            f.jdbc().update("UPDATE sys_user SET auth_revision=2 WHERE user_id=42");
            revocations.enqueue(List.of(42L, 99999L));
            return null;
        }));
        assertEquals(1, f.accounts().revision(42));
        verifyNoInteractions(redis);
        assertThrows(IllegalArgumentException.class, () -> f.platform()
                .run(() -> {
                    revocations.enqueueUsername("missing");
                    return null;
                }));
    }

    @Test
    void failureBackoffIsCappedAndSameVersionDoesNotResetIt() throws Exception {
        var f = fixture();
        var redis = mock(UserSessionStore.class);
        f.accounts().enqueue(List.of(42L));
        f.jdbc().update("UPDATE sys_session_revocation SET attempts=20");
        doThrow(new IllegalStateException("TEST ONLY failure")).when(redis).revoke(42, 1);
        new SessionRevocationService(f.accounts(), redis, f.platform().transactionManager()).retryPending();
        Timestamp next = f.jdbc().queryForObject("SELECT next_attempt_at FROM sys_session_revocation", Timestamp.class);
        Timestamp now = f.jdbc().queryForObject("SELECT CURRENT_TIMESTAMP", Timestamp.class);
        assertTrue(next.getTime() - now.getTime() >= 299000);
        assertTrue(next.getTime() - now.getTime() <= 300000);
        f.accounts().enqueue(List.of(42L));
        assertEquals(next, f.jdbc()
                .queryForObject("SELECT next_attempt_at FROM sys_session_revocation", Timestamp.class));
        assertEquals(21, f.jdbc().queryForObject("SELECT attempts FROM sys_session_revocation", Integer.class));
    }

    @Test
    void redisSuccessButAcknowledgementFailureRemainsRecoverable() throws Exception {
        var f = fixture();
        var accounts = spy(f.accounts());
        var redis = mock(UserSessionStore.class);
        f.accounts().enqueue(List.of(42L));
        doThrow(new IllegalStateException("TEST ONLY acknowledgement unavailable")).when(accounts).delivered(42, 1);
        var revocations = new SessionRevocationService(accounts, redis, f.platform().transactionManager());
        revocations.retryPending();
        verify(redis).revoke(42, 1);
        assertEquals(1, f.jdbc().queryForObject("SELECT attempts FROM sys_session_revocation", Integer.class));
        assertEquals("Session revocation delivery or acknowledgement failed",
                f.jdbc().queryForObject("SELECT last_error FROM sys_session_revocation", String.class));
        reset(accounts);
        f.jdbc().update("UPDATE sys_session_revocation SET next_attempt_at=CURRENT_TIMESTAMP");
        revocations.retryPending();
        assertEquals(List.of(), f.accounts().pending());
        verify(redis, times(2)).revoke(42, 1);
    }
}
