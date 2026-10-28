package dev.mesbase.framework.openapi;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.mesbase.common.core.redis.SystemCacheNamespace;
import dev.mesbase.common.core.redis.RedisKeySegment;
import dev.mesbase.common.openapi.OpenApiError;
import dev.mesbase.common.openapi.OpenApiException;
import dev.mesbase.common.openapi.OpenApiPrincipal;
import dev.mesbase.system.openapi.dto.OpenApiCredentialRequest;
import dev.mesbase.system.openapi.dto.OpenApiTokenCheck;
import dev.mesbase.system.openapi.service.OpenApiClientService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.util.Base64;
import java.util.Collections;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = {"mes.system.enabled", "open-api.enabled"}, havingValue = "true")
public class OpenApiTokenService {
    private final StringRedisTemplate redis;
    private final OpenApiClientService clients;
    private final OpenApiProperties properties;
    private final ObjectMapper json;
    private final SystemCacheNamespace namespace;
    private final Clock clock;
    private final OpenApiRateLimiter limiter;
    private final SecureRandom random = new SecureRandom();
    // 单次Lua完成查重、续期或替换及两条记录写入，多副本并发返回同一Token。
    private static final DefaultRedisScript<String> ISSUE = new DefaultRedisScript<>(
            "local candidate=cjson.decode(ARGV[1]); local record=cjson.decode(ARGV[2]); " +
                    "local previous=redis.call('GET',KEYS[1]); " +
                    "if previous then local old=cjson.decode(previous); " +
                    "if old.credentialVersion==candidate.credentialVersion and old.expiresAt>tonumber(ARGV[4]) " +
                    "and redis.call('EXISTS',ARGV[3]..old.tokenId)==1 then " +
                    "candidate.accessToken=old.accessToken; candidate.tokenDigest=old.tokenDigest; candidate.tokenId=old.tokenId; " +
                    "else redis.call('DEL',ARGV[3]..old.tokenId); end; end; " +
                    "record.tokenDigest=candidate.tokenDigest; record.tokenId=candidate.tokenId; " +
                    "redis.call('SET',ARGV[3]..candidate.tokenId,cjson.encode(record),'EX',ARGV[5]); " +
                    "redis.call('SET',KEYS[1],cjson.encode(candidate),'EX',ARGV[5]); return candidate.accessToken;", String.class);

    public OpenApiTokenService(StringRedisTemplate redis, OpenApiClientService clients, OpenApiProperties properties,
                               @Qualifier("openApiJson") ObjectMapper json, @Qualifier("openApiClock") Clock clock,
                               OpenApiRateLimiter limiter,
                               SystemCacheNamespace namespace) {
        this.redis = redis;
        this.clients = clients;
        this.properties = properties;
        this.json = json;
        this.clock = clock;
        this.limiter = limiter;
        this.namespace = namespace;
    }

    public OpenApiTokenVo issue(OpenApiCredentialRequest request) {
        OpenApiPrincipal principal = clients.authenticate(request);
        // 已验证身份供本次专用安全链使用，限流/Redis写入失败也能关联调用方。
        SecurityContextHolder.getContext().setAuthentication(new OpenApiAuthentication(principal));
        limiter.tokenClient(principal);
        return issueVerified(principal);
    }

    OpenApiTokenVo issueVerified(OpenApiPrincipal principal) {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String secret = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        String tokenId = UUID.randomUUID().toString();
        String token = "oa_" + tokenId + "." + secret;
        OpenApiTokenRecord record = new OpenApiTokenRecord();
        record.setClientId(principal.getClientId());
        record.setCredentialVersion(principal.getCredentialVersion());
        record.setExpiresAt(clock.millis() + properties.getTokenTtlSeconds() * 1000L);
        record.setTokenId(tokenId);
        record.setTokenDigest(digest(secret));
        OpenApiClientTokenRecord current = new OpenApiClientTokenRecord();
        current.setAccessToken(token);
        current.setTokenDigest(digest(secret));
        current.setTokenId(tokenId);
        current.setCredentialVersion(principal.getCredentialVersion());
        current.setExpiresAt(record.getExpiresAt());
        try {
            token = redis.execute(ISSUE, Collections.singletonList(tokenPrefix() + "client:" + RedisKeySegment.encode(String.valueOf(principal.getClientId()))),
                    json.writeValueAsString(current), json.writeValueAsString(record), tokenPrefix() + "token:",
                    String.valueOf(clock.millis()), String.valueOf(properties.getTokenTtlSeconds()));
            if (token == null) throw new OpenApiException(OpenApiError.DEPENDENCY_UNAVAILABLE);
        } catch (JsonProcessingException e) {
            throw new OpenApiException(OpenApiError.INTERNAL_ERROR, e);
        }
        return new OpenApiTokenVo(token, properties.getTokenTtlSeconds());
    }

    public OpenApiPrincipal authenticate(String token) {
        TokenParts parts = validateFormat(token);
        String value = redis.opsForValue().get(key(parts.tokenId()));
        if (value == null) throw new OpenApiException(OpenApiError.INVALID_CREDENTIALS);
        OpenApiTokenRecord record;
        try {
            record = json.readValue(value, OpenApiTokenRecord.class);
        } catch (JsonProcessingException e) {
            throw new OpenApiException(OpenApiError.DEPENDENCY_UNAVAILABLE, e);
        }
        if (record == null || record.getClientId() == null || record.getCredentialVersion() < 1)
            throw new OpenApiException(OpenApiError.DEPENDENCY_UNAVAILABLE);
        if (!constantTimeEquals(record.getTokenDigest(), digest(parts.secret())))
            throw new OpenApiException(OpenApiError.INVALID_CREDENTIALS);
        if (record.getExpiresAt() <= clock.millis()) throw new OpenApiException(OpenApiError.INVALID_CREDENTIALS);
        return clients.validateToken(new OpenApiTokenCheck(record.getClientId(), record.getCredentialVersion()));
    }

    public void revoke(String token) {
        TokenParts parts = validateFormat(token);
        String key = key(parts.tokenId());
        String value = redis.opsForValue().get(key);
        if (value == null) return;
        try {
            OpenApiTokenRecord record = json.readValue(value, OpenApiTokenRecord.class);
            if (record == null || !constantTimeEquals(record.getTokenDigest(), digest(parts.secret())))
                throw new OpenApiException(OpenApiError.INVALID_CREDENTIALS);
            redis.delete(key);
        } catch (JsonProcessingException e) {
            throw new OpenApiException(OpenApiError.DEPENDENCY_UNAVAILABLE, e);
        }
    }

    private TokenParts validateFormat(String token) {
        if (token == null || !token.matches("oa_[A-Fa-f0-9-]{36}\\.[A-Za-z0-9_-]{43}"))
            throw new OpenApiException(OpenApiError.INVALID_CREDENTIALS);
        int separator = token.indexOf('.');
        return new TokenParts(token.substring(3, separator), token.substring(separator + 1));
    }

    // 相关key使用同一hash tag，Lua涉及的动态Token key也落在同一Redis Cluster slot。
    private String tokenPrefix() {
        return namespace.key(properties.keyPrefix() + "{tokens}:");
    }

    private String key(String tokenId) {
        return tokenPrefix() + "token:" + tokenId;
    }

    private static boolean constantTimeEquals(String first, String second) {
        return first != null && MessageDigest.isEqual(first.getBytes(StandardCharsets.UTF_8), second.getBytes(StandardCharsets.UTF_8));
    }

    private record TokenParts(String tokenId, String secret) { }

    static String digest(String value) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
