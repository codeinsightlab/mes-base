package dev.mesbase.platform.codegen;

import dev.mesbase.platform.codegen.ScaffoldWriter.Artifact;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.UnaryOperator;

/**
 * Formats staged Java with the repository's native IDEA formatter before publishing or hashing.
 */
public final class IdeaGeneratedJavaFormatter implements UnaryOperator<List<Artifact>> {
    private final Path launcher;

    public IdeaGeneratedJavaFormatter(Path launcher) {
        this.launcher = launcher.toAbsolutePath().normalize();
    }

    @Override
    public List<Artifact> apply(List<Artifact> artifacts) {
        Path staging = null;
        RuntimeException failure = null;
        Process process = null;
        try {
            if (!Files.isExecutable(launcher)) {
                throw new IOException("Repository IDEA formatter unavailable");
            }
            staging = Files.createTempDirectory("mes-codegen-idea-");
            List<String> command = new ArrayList<>();
            command.add(launcher.toString());
            for (Artifact artifact : artifacts) {
                if (!artifact.removed() && artifact.relativePath().endsWith(".java")) {
                    Path source = staging.resolve(artifact.relativePath());
                    Files.createDirectories(source.getParent());
                    Files.writeString(source, artifact.content());
                    command.add(source.toString());
                }
            }
            process = new ProcessBuilder(command).redirectErrorStream(true)
                    .redirectOutput(staging.resolve("formatter.log").toFile()).start();
            if (!process.waitFor(60, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                throw new IOException("IDEA formatter timed out");
            }
            if (process.exitValue() != 0) {
                throw new IOException("IDEA formatter failed");
            }
            List<Artifact> formatted = new ArrayList<>();
            for (Artifact artifact : artifacts) {
                formatted.add(!artifact.removed() && artifact.relativePath().endsWith(".java")
                        ? new Artifact(artifact.target(), artifact.relativePath(),
                        Files.readString(staging.resolve(artifact.relativePath())))
                        : artifact);
            }
            return List.copyOf(formatted);
        } catch (InterruptedException interrupted) {
            if (process != null) {
                process.destroyForcibly();
            }
            Thread.currentThread().interrupt();
            failure = new CodegenException(CodegenException.Code.TEMPLATE_FAILURE, interrupted);
            throw failure;
        } catch (IOException exception) {
            failure = new CodegenException(CodegenException.Code.TEMPLATE_FAILURE, exception);
            throw failure;
        } finally {
            if (staging != null) {
                try (var paths = Files.walk(staging)) {
                    for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                        Files.delete(path);
                    }
                } catch (IOException cleanup) {
                    if (failure != null) {
                        failure.addSuppressed(cleanup);
                    } else {
                        throw new CodegenException(CodegenException.Code.OUTPUT_FAILURE, cleanup);
                    }
                }
            }
        }
    }
}
