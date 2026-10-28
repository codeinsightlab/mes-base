package dev.mesbase.server;

import dev.mesbase.common.cache.DetailCacheKeys;
import dev.mesbase.common.core.redis.RedisKeySegment;
import dev.mesbase.system.domain.SysUserCacheResource;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ReadableRedisKeyTest {
    @Test
    void detailIdsStayReadableAndNestedResourcesHaveBoundedPatterns() {
        String parent = DetailCacheKeys.detail("business:material", "0007");
        String nested = DetailCacheKeys.detail("business:material:category", "0007");
        assertTrue(parent.endsWith("detail:0007"));
        assertTrue(nested.endsWith("detail:0007"));
        assertNotEquals(parent, nested);
        String parentPattern = DetailCacheKeys.detailPattern("business:material");
        String childPattern = DetailCacheKeys.detailPattern("business:material:category");
        assertTrue(parentPattern.endsWith("business:material:detail:*"));
        assertTrue(childPattern.endsWith("business:material:category:detail:*"));
        assertTrue(!childPattern.startsWith("business:material:detail:"));
        assertEquals("A:%{x}", RedisKeySegment.decode(RedisKeySegment.encode("A:%{x}")));
        assertThrows(IllegalArgumentException.class, () -> RedisKeySegment.decode("%41"));
        assertThrows(IllegalArgumentException.class, () -> RedisKeySegment.decode("%FF"));
        assertTrue(DetailCacheKeys.detail(SysUserCacheResource.PREFIX, "0007").contains("{sys_user}"));
        assertTrue(DetailCacheKeys.version(SysUserCacheResource.PREFIX).contains("{sys_user}"));
    }
}
