package dev.mesbase.framework.openapi;

import dev.mesbase.common.core.redis.SystemCacheNamespace;
import dev.mesbase.common.openapi.*;
import java.util.List;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

/** SOURCE_PRODUCT_PORT: atomic Source Redis limit; use string arguments and owned DB namespace. */
@Component
@ConditionalOnProperty(name={"mes.system.enabled","open-api.enabled"},havingValue="true")
public class OpenApiRateLimiter {
    private static final DefaultRedisScript<Long> LIMIT = new DefaultRedisScript<>(
            "local current=tonumber(redis.call('GET',KEYS[1]) or '0'); " +
            "if current>=tonumber(ARGV[1]) then return current+1; end; " +
            "current=redis.call('INCR',KEYS[1]); if current==1 then redis.call('EXPIRE',KEYS[1],ARGV[2]); end; return current;", Long.class);
    private final StringRedisTemplate redis;
    private final OpenApiProperties properties;
    private final SystemCacheNamespace namespace;
    public OpenApiRateLimiter(StringRedisTemplate redis, OpenApiProperties properties, SystemCacheNamespace namespace) {
        this.redis = redis; this.properties = properties; this.namespace = namespace;
    }
    public void tokenRequest(String peer) { check("token-ip:"+OpenApiTokenService.digest(peer), properties.getTokenRateLimit()); }
    public void tokenClient(OpenApiPrincipal principal) { check("token-client:"+principal.getClientId(), properties.getTokenRateLimit()); }
    public void api(OpenApiInvocation invocation) {
        check("client:"+invocation.getPrincipal().getClientId()+":api:"+invocation.getApiCode(), properties.getApiRateLimit());
    }
    private void check(String suffix, int count) {
        Long value = redis.execute(LIMIT, List.of(namespace.key(properties.keyPrefix()+"rate:"+suffix)),
                String.valueOf(count), String.valueOf(properties.getRateWindowSeconds()));
        if(value==null) throw new OpenApiException(OpenApiError.DEPENDENCY_UNAVAILABLE);
        if(value>count) throw new OpenApiException(OpenApiError.RATE_LIMITED);
    }
}
