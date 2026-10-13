package dev.mesbase.platform.codegen;

import dev.mesbase.platform.codegen.ScaffoldWriter.Artifact;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IdeaGeneratedJavaFormatterTest {
    @TempDir
    Path root;

    private Path launcher() throws Exception {
        Path launcher = root.resolve("scripts/format-java");
        Files.createDirectories(launcher.getParent());
        Files.writeString(launcher, "#!/bin/sh\nset -eu\necho run >> '" + root.resolve("runs")
                + "'\nfor file in \"$@\"; do printf '\\n// formatted\\n' >> \"$file\"; done\n");
        assertTrue(launcher.toFile().setExecutable(true));
        return launcher;
    }

    private Artifact java(String content) {
        return new Artifact(CodegenResult.Target.BACKEND, "src/main/java/sample/Probe.java", content);
    }

    @Test
    void nonJavaAndRemovalOnlyNeverStartFormatter() {
        var artifacts = List.of(new Artifact(CodegenResult.Target.FRONTEND, "src/api/probe.ts", "export const api = {}"),
                new Artifact(CodegenResult.Target.BACKEND, "src/main/java/Removed.java", "", true));
        assertEquals(artifacts, new IdeaGeneratedJavaFormatter(root.resolve("missing")).apply(artifacts));
    }

    @Test
    void persistsAcrossInstancesButInvalidatesChangedSourceSettingsAndLauncher() throws Exception {
        Path launcher = launcher();
        Path cache = root.resolve("target/cache");
        var source = List.of(java("package sample; class Probe {}"));
        var first = new IdeaGeneratedJavaFormatter(launcher, cache).apply(source);
        assertTrue(first.getFirst().content().endsWith("// formatted\n"));
        assertEquals(first, new IdeaGeneratedJavaFormatter(launcher, cache).apply(source));
        assertEquals(1, Files.readAllLines(root.resolve("runs")).size());
        new IdeaGeneratedJavaFormatter(launcher, cache).apply(List.of(java("package sample; class Probe { int value; }")));
        assertEquals(2, Files.readAllLines(root.resolve("runs")).size());
        Files.createDirectories(root.resolve(".idea/codeStyles"));
        Files.writeString(root.resolve(".idea/codeStyles/Project.xml"), "changed formatter settings");
        new IdeaGeneratedJavaFormatter(launcher, cache).apply(source);
        assertEquals(3, Files.readAllLines(root.resolve("runs")).size());
        Files.writeString(launcher, Files.readString(launcher) + "# changed tool\n");
        new IdeaGeneratedJavaFormatter(launcher, cache).apply(source);
        assertEquals(4, Files.readAllLines(root.resolve("runs")).size());
    }

    @Test
    void corruptedCacheFallsBackAndDoesNotHideNativeFailure() throws Exception {
        Path launcher = launcher();
        Path cache = root.resolve("target/cache");
        var formatter = new IdeaGeneratedJavaFormatter(launcher, cache);
        var source = List.of(java("package sample; class Probe {}"));
        var expected = formatter.apply(source);
        try (var files = Files.list(cache)) {
            Files.writeString(files.findFirst().orElseThrow(), "corrupt");
        }
        assertEquals(expected, formatter.apply(source));
        assertEquals(2, Files.readAllLines(root.resolve("runs")).size());
        Files.writeString(launcher, "#!/bin/sh\nexit 1\n");
        assertThrows(CodegenException.class, () -> formatter.apply(source));
    }

    @Test
    void cacheWriteFailureDoesNotTurnSuccessfulFormattingIntoFailure() throws Exception {
        Path launcher = launcher();
        Path parent = root.resolve("not-a-directory");
        Files.writeString(parent, "fixture");
        var formatted = new IdeaGeneratedJavaFormatter(launcher, parent.resolve("cache"))
                .apply(List.of(java("package sample; class Probe {}")));
        assertTrue(formatted.getFirst().content().contains("// formatted"));
    }

    @Test
    void cacheHitStillGoesThroughOutputConflictChecks() throws Exception {
        Path launcher = launcher();
        var formatter = new IdeaGeneratedJavaFormatter(launcher, root.resolve("target/cache"));
        var source = List.of(java("// MES-CODEGEN\npackage sample; class Probe {}"));
        var request = new CodegenRequest("TEST_ONLY", null, "probe", "sample", "测试", "sample", "TEST_ONLY",
                CodegenRequest.ScopeKind.FACTORY_SCOPED, CodegenRequest.OverwritePolicy.REPLACE_GENERATED,
                false, root.toRealPath().resolve("backend"), root.toRealPath().resolve("frontend"));
        var writer = new ScaffoldWriter();
        writer.write(formatter.apply(source), request);
        Files.writeString(request.targetBackendRoot().resolve(source.getFirst().relativePath()), "user owned source");
        var failure = assertThrows(CodegenException.class, () -> writer.write(formatter.apply(source), request));
        assertEquals(CodegenException.Code.OUTPUT_CONFLICT, failure.code());
        assertEquals(1, Files.readAllLines(root.resolve("runs")).size());
    }
}
