package dev.mesbase.framework.web.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.mesbase.common.core.domain.entity.SysUser;
import dev.mesbase.common.core.domain.model.LoginUser;
import dev.mesbase.common.core.redis.UserSessionStore;
import dev.mesbase.core.error.ErrorCode;
import dev.mesbase.core.error.FoundationException;
import dev.mesbase.system.mapper.SessionAccountMapper;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Set;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

/**
 * SOURCE_PRODUCT_PORT: Redis identity restoration; current roles are request-local, never persisted permissions.
 */
@Service
@ConditionalOnProperty(name = "mes.system.enabled", havingValue = "true")
public class TokenService {
    public static final String PREFIX = UserSessionStore.PREFIX;
    private final UserSessionStore sessions;
    private final SessionAccountMapper accounts;
    private final ObjectMapper json = new ObjectMapper();
    private final int ttl;

    public TokenService(UserSessionStore sessions, SessionAccountMapper accounts, Environment environment) {
        this.sessions = sessions;
        this.accounts = accounts;
        ttl = environment.getProperty("token.expire-time", Integer.class, 60);
        if (ttl < 1 || ttl > 1440) throw new IllegalArgumentException("Token lifetime invalid");
    }

    public static String hash(String token) {
        try {
            return HexFormat.of()
                    .formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    public record Identity(Long userId, String username, Long deptId, String nickName, String avatar,
                           long loginTime, String ip, String browser, String os, long authRevision) {
    }

    public String createToken(LoginUser user, HttpServletRequest request) {
        // Fence a login racing with a committed account change before publishing to Redis.
        SysUser current = accounts.account(user.getUserId());
        valid(current, user.getUserId(), user.getUsername(), user.getUser().getAuthRevision());
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        ClientAgent agent = ClientAgent.parse(request.getHeader("User-Agent"));
        Identity identity = new Identity(user.getUserId(), user.getUsername(), user.getDeptId(),
                user.getUser().getNickName(), user.getUser().getAvatar(), System.currentTimeMillis(),
                request.getRemoteAddr(), agent.browser(), agent.os(), user.getUser().getAuthRevision());
        try {
            if (!sessions.issue(hash(raw), identity.userId(), identity.authRevision(), json.writeValueAsString(identity),
                    Instant.now().plusSeconds(ttl * 60L))) throw denied();
        } catch (JsonProcessingException impossible) {
            throw new IllegalStateException(impossible);
        }
        return raw;
    }

    public LoginUser getLoginUser(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) return null;
        return resolve(header.substring(7));
    }

    public Identity identity(String digest) {
        String payload = sessions.read(digest);
        if (payload == null) return null;
        try {
            return json.readValue(payload, Identity.class);
        } catch (JsonProcessingException invalid) {
            throw denied();
        }
    }

    public LoginUser resolve(String raw) {
        if (!raw.matches("[A-Za-z0-9_-]{43}")) throw denied();
        String digest = hash(raw);
        Identity identity = identity(digest);
        if (identity == null) throw denied();
        // One password-free admission query loads current roles and the durable account fence.
        // It also rejects stale credentials when DB committed but Redis delivery is pending.
        SysUser user = accounts.account(identity.userId());
        valid(user, identity.userId(), identity.username(), identity.authRevision());
        LoginUser login = new LoginUser(user.getUserId(), user.getDeptId(), user, Set.of());
        login.setToken(digest);
        login.setLoginTime(identity.loginTime());
        login.setIpaddr(identity.ip());
        login.setBrowser(identity.browser());
        login.setOs(identity.os());
        return login;
    }

    private static void valid(SysUser user, Long id, String username, long revision) {
        if (user == null || !"0".equals(user.getStatus()) || !"0".equals(user.getDelFlag())
                || !id.equals(user.getUserId()) || !username.equals(user.getUserName()) || revision != user.getAuthRevision())
            throw denied();
    }

    public void delLoginUser(String digest) {
        sessions.logout(digest);
    }

    public int ttlMinutes() {
        return ttl;
    }

    private static FoundationException denied() {
        return new FoundationException(ErrorCode.UNAUTHENTICATED);
    }
}
