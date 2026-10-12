package dev.mesbase.platform.codegen;

import java.nio.file.Files;
import java.nio.file.Path;
import tools.jackson.databind.json.JsonMapper;

/**
 * Arguments: configuration, repository root, report file. Never prints connection properties.
 */
public final class BusinessCodegenCli {
    private BusinessCodegenCli() {
    }

    public static void main(String[] args) {
        try {
            if (args.length != 3)
                throw new IllegalArgumentException("Expected configuration, repository root, report file");
            var config = BusinessCodegenConfiguration.read(Path.of(args[0]));
            var report = new BusinessCodegenRunner().run(config, Path.of(args[1]));
            Path output = Path.of(args[2]).toAbsolutePath();
            Files.createDirectories(output.getParent());
            Files.writeString(output, new JsonMapper().writerWithDefaultPrettyPrinter().writeValueAsString(report));
            System.out.println("Codegen " + report.status() + ": " + report.tables().size() + " supported, "
                    + report.rejected().size() + " rejected; report=" + output);
            if (!report.rejected().isEmpty()) System.exit(2);
        } catch (Exception failure) {
            System.err.println("Codegen failed: " + failure.getClass().getSimpleName());
            System.exit(2);
        }
    }
}
