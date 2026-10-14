package dev.mesbase;

import dev.mesbase.common.annotation.Log;
import dev.mesbase.common.core.domain.entity.SysUser;
import dev.mesbase.common.core.domain.model.LoginUser;
import dev.mesbase.common.core.redis.SystemCacheNamespace;
import dev.mesbase.common.utils.SecurityUtils;
import dev.mesbase.core.error.FoundationException;
import dev.mesbase.core.factory.FactoryId;
import dev.mesbase.core.factory.FactoryScope;
import dev.mesbase.framework.aspectj.LogAspect;
import dev.mesbase.framework.web.service.CaptchaService;
import dev.mesbase.framework.web.service.SysLoginService;
import dev.mesbase.framework.web.service.TokenService;
import dev.mesbase.platform.execution.FactoryContext;
import dev.mesbase.platform.web.RequestBoundaryFilter;
import dev.mesbase.system.domain.SysLogininfor;
import dev.mesbase.system.domain.SysOperLog;
import dev.mesbase.system.service.ISysLogininforService;
import dev.mesbase.system.service.ISysOperLogService;
import dev.mesbase.system.service.ISysUserService;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * TEST ONLY audit sinks are mocks; existing production AOP/login implementation is exercised.
 */
class HttpLoggingAuditPreservationTest {
    public static class AuditedAction {
        @Log(title = "TEST ONLY audit")
        public String execute(boolean failure) {
            if (failure) throw new IllegalStateException("TEST ONLY failure");
            return "TEST ONLY success";
        }
    }

    @Test
    void operationAuditStillWritesOneOriginalRecordForSuccessAndFailure() throws Exception {
        var sink = mock(ISysOperLogService.class);
        var proxy = new AspectJProxyFactory(new AuditedAction());
        proxy.addAspect(new LogAspect(sink));
        AuditedAction action = proxy.getProxy();
        var user = new SysUser();
        user.setUserId(42L);
        user.setUserName("TEST_ONLY_USER");
        var login = new LoginUser(42L, 1L, user, Set.of());
        try {
            for (boolean failure : List.of(false, true)) {
                var request = new MockHttpServletRequest("POST", "/api/test-only/audit");
                RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
                new RequestBoundaryFilter().doFilter(request, new MockHttpServletResponse(), (req, res) -> {
                    SecurityContextHolder.getContext().setAuthentication(
                            UsernamePasswordAuthenticationToken.authenticated(login, null, List.of()));
                    FactoryContext.bind(new FactoryScope(new FactoryId("TEST_ONLY_FACTORY")));
                    if (failure) assertThrows(IllegalStateException.class, () -> action.execute(true));
                    else assertEquals("TEST ONLY success", action.execute(false));
                });
            }
            var rows = ArgumentCaptor.forClass(SysOperLog.class);
            verify(sink, times(2)).insertOperlog(rows.capture());
            assertEquals(0, rows.getAllValues().getFirst().getStatus());
            assertEquals(1, rows.getAllValues().getLast().getStatus());
            for (var row : rows.getAllValues()) {
                assertEquals("42", row.getActorId());
                assertEquals("TEST_ONLY_FACTORY", row.getFactoryId());
                assertNotNull(row.getRequestId());
                assertEquals("/api/test-only/audit", row.getOperUrl());
            }
        } finally {
            SecurityContextHolder.clearContext();
            RequestContextHolder.resetRequestAttributes();
            FactoryContext.clear();
        }
    }

    @Test
    void loginAuditStillRecordsBothSuccessfulAndRejectedPasswordsWithServerRequestId() throws Exception {
        var users = mock(ISysUserService.class);
        var logs = mock(ISysLogininforService.class);
        var tokens = mock(TokenService.class);
        var redis = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked") ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        var env = new MockEnvironment().withProperty("mes.platform.url", "jdbc:test-only:audit");
        var user = new SysUser();
        user.setUserId(42L);
        user.setUserName("TEST_ONLY_USER");
        user.setPassword(SecurityUtils.encryptPassword("TEST_ONLY_PASSWORD"));
        user.setStatus("0");
        user.setDelFlag("0");
        when(users.selectUserByUserName("TEST_ONLY_USER")).thenReturn(user);
        var service = new SysLoginService(users, logs, tokens, redis, mock(CaptchaService.class), env, new SystemCacheNamespace(env));
        for (boolean success : List.of(true, false)) {
            var request = new MockHttpServletRequest("POST", "/api/session/login");
            new RequestBoundaryFilter().doFilter(request, new MockHttpServletResponse(), (req, res) -> {
                if (success)
                    service.login("TEST_ONLY_USER", "TEST_ONLY_PASSWORD", "TEST_ONLY_CODE", "TEST_ONLY_UUID", request);
                else assertThrows(FoundationException.class,
                        () -> service.login("TEST_ONLY_USER", "TEST_ONLY_WRONG", "TEST_ONLY_CODE", "TEST_ONLY_UUID", request));
            });
        }
        var rows = ArgumentCaptor.forClass(SysLogininfor.class);
        verify(logs, times(2)).insertLogininfor(rows.capture());
        assertEquals("0", rows.getAllValues().getFirst().getStatus());
        assertEquals("1", rows.getAllValues().getLast().getStatus());
        for (var row : rows.getAllValues()) {
            assertEquals("42", row.getActorId());
            assertNotNull(row.getRequestId());
        }
    }
}
