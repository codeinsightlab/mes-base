package dev.mesbase.server;

import dev.mesbase.common.core.domain.entity.SysUser;
import dev.mesbase.common.core.domain.model.LoginUser;
import dev.mesbase.framework.security.SystemTokenFilter;
import dev.mesbase.framework.web.service.TokenService;
import jakarta.servlet.DispatcherType;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SystemTokenFilterAdmissionTest {
    @Test
    void admittedRequestAsyncRedispatchUsesOriginalIdentityAndNextRequestReadmits() throws Exception {
        var tokens = mock(TokenService.class);
        var request = new MockHttpServletRequest("GET", "/api/personal/profile");
        var account = new SysUser();
        account.setUserId(42L);
        account.setUserName("fixture");
        var login = new LoginUser(account, Set.of());
        when(tokens.getLoginUser(request)).thenReturn(login);
        var filter = new SystemTokenFilter(tokens);
        try {
            filter.doFilter(request, new MockHttpServletResponse(), (req, res) ->
                    assertSame(login, SecurityContextHolder.getContext().getAuthentication().getPrincipal()));
            SecurityContextHolder.clearContext();
            request.setDispatcherType(DispatcherType.ASYNC);
            filter.doFilter(request, new MockHttpServletResponse(), (req, res) ->
                    assertSame(login, SecurityContextHolder.getContext().getAuthentication().getPrincipal()));
            verify(tokens, times(1)).getLoginUser(request);
            var next = new MockHttpServletRequest("GET", "/api/personal/profile");
            filter.doFilter(next, new MockHttpServletResponse(), (req, res) -> {
            });
            verify(tokens).getLoginUser(next);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    void redisFailureFailsAdmissionWithSafe503AndDoesNotExecuteEndpoint() throws Exception {
        var tokens = mock(TokenService.class);
        var request = new MockHttpServletRequest();
        when(tokens.getLoginUser(request)).thenThrow(new RedisConnectionFailureException("TEST ONLY internal host"));
        var response = new MockHttpServletResponse();
        new SystemTokenFilter(tokens).doFilter(request, response, (req, res) -> {
            throw new AssertionError("must not execute");
        });
        assertEquals(503, response.getStatus());
        assertTrue(response.getContentAsString().contains("AUTHENTICATION_UNAVAILABLE"));
        assertFalse(response.getContentAsString().contains("internal host"));
    }
}
