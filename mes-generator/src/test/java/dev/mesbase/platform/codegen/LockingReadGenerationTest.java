package dev.mesbase.platform.codegen;

import dev.mesbase.platform.codegen.TableMetadata.Column;
import java.nio.file.Path;
import java.sql.Types;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertTrue;

class LockingReadGenerationTest {
    @TempDir
    Path temporary;

    @Test
    void lockingReadKeepsOriginalPrimaryKeyMappingAndBypassesBothCaches() {
        var request = new CodegenRequest("TEST_ONLY", null, "lock_probe", "probe", "测试",
                "sample.probe", "TEST_ONLY", CodegenRequest.ScopeKind.FACTORY_SCOPED,
                CodegenRequest.OverwritePolicy.FAIL, false, temporary.resolve("backend"),
                temporary.resolve("frontend"), "mes-business", false, null);
        var metadata = new TableMetadata(null, null, "lock_probe", "测试", List.of(
                new Column("probe_key", "probeKey", Types.VARCHAR, "String", 64, 0,
                        false, true, false, null, "主键"),
                new Column("state", "state", Types.VARCHAR, "String", 32, 0,
                        false, false, false, null, "状态")), List.of(), List.of());
        var artifacts = new MyBatisPersistenceRenderer().render(request, metadata, "LockProbe");
        var mapper = artifacts.stream().filter(a -> a.relativePath().endsWith("Mapper.java"))
                .findFirst().orElseThrow().content();
        var xml = artifacts.stream().filter(a -> a.relativePath().endsWith("Mapper.xml"))
                .findFirst().orElseThrow().content();
        assertTrue(mapper.contains("selectByPrimaryKey(String probeKey)"));
        assertTrue(mapper.contains("selectByPrimaryKeyForUpdate(String probeKey)"));
        assertTrue(xml.contains("id=\"selectByPrimaryKey\""));
        assertTrue(xml.contains("id=\"selectByPrimaryKeyForUpdate\""));
        int lockingId = xml.indexOf("id=\"selectByPrimaryKeyForUpdate\"");
        var lockingSelect = xml.substring(xml.lastIndexOf("<select", lockingId));
        lockingSelect = lockingSelect.substring(0, lockingSelect.indexOf("</select>"));
        assertTrue(lockingSelect.contains("flushCache=\"true\""));
        assertTrue(lockingSelect.contains("useCache=\"false\""));
        assertTrue(lockingSelect.contains("where probe_key = #{probeKey,jdbcType=VARCHAR}"));
        assertTrue(lockingSelect.contains("for update"));
    }
}
