package dev.mesbase.framework.security;

import dev.mesbase.core.error.FoundationException;
import dev.mesbase.core.error.ErrorCode;
import org.springframework.dao.DataAccessException;
import dev.mesbase.common.core.domain.model.LoginUser;
import dev.mesbase.framework.web.service.TokenService;
import dev.mesbase.platform.web.ErrorWriter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

public final class SystemTokenFilter extends OncePerRequestFilter {
    private final TokenService tokens;

    public SystemTokenFilter(TokenService tokens) {
        this.tokens = tokens;
    }

    @Override
    protected boolean shouldNotFilterAsyncDispatch() {
        return false;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws IOException, ServletException {
        request.setAttribute("mes.authScheme", "Bearer");
        try {
            LoginUser user = (LoginUser) request.getAttribute(SystemTokenFilter.class.getName());
            if (user == null) {
                user = tokens.getLoginUser(request);
                if (user != null) request.setAttribute(SystemTokenFilter.class.getName(), user);
            }
            if (user != null) {
                var auth = UsernamePasswordAuthenticationToken.authenticated(user, null, List.of());
                SecurityContextHolder.getContext().setAuthentication(auth);
            }
        } catch (DataAccessException failure) {
            logger.error("Session admission unavailable", failure);
            ErrorWriter.write(ErrorCode.AUTHENTICATION_UNAVAILABLE, request, response);
            return;
        } catch (FoundationException failure) {
            ErrorWriter.write(failure.code(), request, response);
            return;
        }
        chain.doFilter(request, response);
    }
}
