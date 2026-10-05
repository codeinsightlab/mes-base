package dev.mesbase;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import org.springframework.web.method.HandlerMethod;
import dev.mesbase.platform.web.EndpointCatalog;
import java.nio.file.*;
class ArchitectureBoundaryTest {
    static class Unclassified { public String route() { return "test-only"; } }
    @Test void unclassifiedEndpointFailsStartupCatalog() throws Exception { assertThrows(IllegalStateException.class,() -> EndpointCatalog.required(new HandlerMethod(new Unclassified(),Unclassified.class.getMethod("route")))); }
    @Test void coreRemainsIndependentFromThreadLocalAndFramework() throws Exception {
        try(var paths=Files.walk(Path.of("src/main/java/dev/mesbase/core"))) { for(Path file:paths.filter(p -> p.toString().endsWith(".java")).toList()) {
            String source=Files.readString(file);for(String forbidden:java.util.List.of("ThreadLocal","org.springframework","dev.mesbase.infrastructure","dev.mesbase.platform","dev.mesbase.integration","com.ktg")) assertFalse(source.contains(forbidden),file+" contains "+forbidden);
        } }
    }
    @Test void syntheticBusinessProbeExistsOnlyInTests() throws Exception {
        try(var paths=Files.walk(Path.of("src/main"))) { for(Path file:paths.filter(Files::isRegularFile).toList()) assertFalse(Files.readString(file).contains("foundation_probe"),file.toString()); }
    }
}
