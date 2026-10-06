package dev.mesbase;

import dev.mesbase.platform.web.EndpointCatalog;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.web.method.HandlerMethod;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ArchitectureBoundaryTest {
    static class Unclassified {
        public String route() {
            return "test-only";
        }
    }

    @Test
    void unclassifiedEndpointFailsStartupCatalog() throws Exception {
        assertThrows(IllegalStateException.class, () -> EndpointCatalog.required(
                new HandlerMethod(new Unclassified(), Unclassified.class.getMethod("route"))));
    }

    @Test
    void coreRemainsIndependentFromThreadLocalAndFramework() throws IOException {
        Path core = Path.of("mes-common/src/main/java/dev/mesbase/core");
        try (var paths = Files.walk(core)) {
            List<Path> sources = paths.filter(p -> p.toString().endsWith(".java")).toList();
            assertFalse(sources.isEmpty(), "Core boundary check must examine real module sources");
            for (Path file : sources) {
                String source = Files.readString(file);
                for (String forbidden : List.of("ThreadLocal", "org.springframework",
                        "dev.mesbase.infrastructure", "dev.mesbase.platform",
                        "dev.mesbase.integration", "com.ktg")) {
                    assertFalse(source.contains(forbidden), file + " contains " + forbidden);
                }
            }
        }
    }

    @Test
    void syntheticBusinessProbeExistsOnlyInTests() throws IOException {
        List<Path> sources = productionFiles();
        assertFalse(sources.isEmpty(), "Probe boundary check must examine real module sources");
        for (Path file : sources) {
            assertFalse(Files.readString(file).contains("foundation_probe"), file.toString());
        }
    }

    private List<Path> productionFiles() throws IOException {
        List<Path> files = new ArrayList<>();
        try (var directories = Files.list(Path.of("."))) {
            List<Path> modules = directories
                    .filter(path -> path.getFileName().toString().startsWith("mes-"))
                    .map(path -> path.resolve("src/main"))
                    .filter(Files::isDirectory)
                    .toList();
            assertFalse(modules.isEmpty(), "Run architecture checks from the Maven reactor root");
            for (Path module : modules) {
                try (var paths = Files.walk(module)) {
                    files.addAll(paths.filter(Files::isRegularFile).toList());
                }
            }
        }
        return files;
    }
}
