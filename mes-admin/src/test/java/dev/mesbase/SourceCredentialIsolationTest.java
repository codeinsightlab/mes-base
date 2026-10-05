package dev.mesbase;

import dev.mesbase.common.core.redis.SystemCacheNamespace;
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
}
