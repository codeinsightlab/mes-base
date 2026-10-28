package dev.mesbase;

import dev.mesbase.common.core.redis.SystemCacheNamespace;
import dev.mesbase.common.core.redis.RedisKeySegment;
import dev.mesbase.common.utils.PasswordPolicy;
import dev.mesbase.common.utils.SecurityUtils;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import static org.junit.jupiter.api.Assertions.*;

class SourceCredentialIsolationTest {
    @Test void credentialsCannotSilentlyTruncateUtf8Passwords() {
        var policy=new PasswordPolicy(new MockEnvironment().withProperty("user.password.min-length","5"));
        String boundary="厂".repeat(24);
        assertTrue(SecurityUtils.matchesPassword(boundary,policy.encode(boundary)));
        assertThrows(dev.mesbase.common.exception.ServiceException.class,()->policy.encode(boundary+"厂"));
        assertFalse(SecurityUtils.matchesPassword(boundary+"厂",policy.encode(boundary)));
        assertFalse(SecurityUtils.matchesPassword(null,policy.encode("test-valid-password")));
    }
    @Test void platformDatabaseOwnsSessionAndSourceCacheNamespace() {
        var first=new SystemCacheNamespace(new MockEnvironment().withProperty("mes.platform.url","jdbc:mysql://localhost/platform_a"));
        var second=new SystemCacheNamespace(new MockEnvironment().withProperty("mes.platform.url","jdbc:mysql://localhost/platform_b"));
        var namedFirst=new SystemCacheNamespace(new MockEnvironment().withProperty("mes.platform.url","jdbc:mysql://localhost/platform_a").withProperty("mes.system.cache-namespace","sameCopiedName"));
        var namedSecond=new SystemCacheNamespace(new MockEnvironment().withProperty("mes.platform.url","jdbc:mysql://localhost/platform_b").withProperty("mes.system.cache-namespace","sameCopiedName"));
        assertNotEquals(namedFirst.key("session:identity"),namedSecond.key("session:identity"));
        assertNotEquals(first.key("session:identity"),second.key("session:identity"));
        assertThrows(IllegalArgumentException.class,()->second.logical(first.key("session:identity")));
        assertEquals("session:identity",first.logical(first.key("session:identity")));
        assertThrows(IllegalArgumentException.class,()->new SystemCacheNamespace(new MockEnvironment().withProperty("mes.system.cache-namespace","foreign:*")));
    }
    @Test void readableSegmentsRoundTripAndLoopbackJdbcAliasesNormalize() {
        for (String value : new String[] {"0007", "AbC", "a:b%*?[]{x}", "工厂#一"})
            assertEquals(value, RedisKeySegment.decode(RedisKeySegment.encode(value)));
        var localName = new SystemCacheNamespace(new MockEnvironment()
                .withProperty("mes.platform.url", "jdbc:mysql://localhost/platform"));
        var ipv4 = new SystemCacheNamespace(new MockEnvironment()
                .withProperty("mes.platform.url", "jdbc:mysql://127.0.0.1:3306/platform"));
        var ipv6 = new SystemCacheNamespace(new MockEnvironment()
                .withProperty("mes.platform.url", "jdbc:mysql://[::1]:3306/platform"));
        var sqlServer = new SystemCacheNamespace(new MockEnvironment()
                .withProperty("mes.platform.url", "jdbc:sqlserver://DB.EXAMPLE:1433;databaseName=platform"));
        assertEquals(localName.key("sample"), ipv4.key("sample"));
        assertEquals(localName.key("sample"), ipv6.key("sample"));
        assertTrue(sqlServer.key("sample").contains(":center:db.example:1433:platform:"));
        assertTrue(localName.key("sample").contains(":center:localhost:3306:platform:"));
    }
}
