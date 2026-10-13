package dev.mesbase.platform.codegen;

import dev.mesbase.platform.codegen.ScaffoldWriter.Artifact;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.UnaryOperator;

/**
 * Formats staged Java with the repository's native IDEA formatter before publishing or hashing.
 */
public final class IdeaGeneratedJavaFormatter implements UnaryOperator<List<Artifact>> {
    private static final Logger LOG = LoggerFactory.getLogger(IdeaGeneratedJavaFormatter.class);
    private static final long MAX_ENTRY_BYTES = 4L * 1024 * 1024;
    private static final long MAX_CACHE_BYTES = 64L * 1024 * 1024;
    private static final int MAX_CACHE_ENTRIES = 512;
    private final Path launcher;
    private final Path cacheDirectory;

    public IdeaGeneratedJavaFormatter(Path launcher) {
        this(launcher, null);
    }

    public IdeaGeneratedJavaFormatter(Path launcher, Path cacheDirectory) {
        this.launcher = launcher.toAbsolutePath().normalize();
        this.cacheDirectory = cacheDirectory == null ? null : cacheDirectory.toAbsolutePath().normalize();
    }

    @Override
    public List<Artifact> apply(List<Artifact> artifacts) {
        List<Artifact> java = artifacts.stream().filter(IdeaGeneratedJavaFormatter::javaSource).toList();
        if (java.isEmpty()) return List.copyOf(artifacts);
        String identity = cacheIdentity();
        Map<Artifact, String> contents = new HashMap<>();
        for (Artifact artifact : java) {
            String cached = readCached(identity, artifact);
            if (cached != null) contents.put(artifact, cached);
        }
        if (contents.size() == java.size()) return replace(artifacts, contents);
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
                if (javaSource(artifact) && !contents.containsKey(artifact)) {
                    Path source = staging.resolve(artifact.relativePath()).normalize();
                    if (!source.startsWith(staging)) throw new IOException("Invalid generated Java path");
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
            for (Artifact artifact : java) {
                if (!contents.containsKey(artifact)) {
                    String content = Files.readString(staging.resolve(artifact.relativePath()));
                    contents.put(artifact, content);
                    writeCached(identity, artifact, content);
                }
            }
            pruneCache();
            return replace(artifacts, contents);
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

    private static boolean javaSource(Artifact artifact) {
        return !artifact.removed() && artifact.relativePath().endsWith(".java");
    }

    private static List<Artifact> replace(List<Artifact> artifacts, Map<Artifact, String> contents) {
        return artifacts.stream().map(artifact -> contents.containsKey(artifact)
                ? new Artifact(artifact.target(), artifact.relativePath(), contents.get(artifact)) : artifact).toList();
    }

    /**
     * Cache is optional; inability to validate the tool identity always falls back to native formatting.
     */
    private String cacheIdentity() {
        if (cacheDirectory == null) return null;
        try {
            if (!Files.isExecutable(launcher) || Files.isSymbolicLink(cacheDirectory)) return null;
            StringBuilder identity = new StringBuilder("mes-java-format-v1\n");
            identity.append(launcher).append('\n').append(Files.readString(launcher));
            Path repository = launcher.getParent().getParent();
            for (String file : List.of(".idea/codeStyles/Project.xml", ".idea/codeStyles/codeStyleConfig.xml", ".editorconfig")) {
                Path settings = repository.resolve(file);
                identity.append(file).append('\n');
                if (Files.exists(settings)) identity.append(Files.readString(settings));
            }
            // The repository launcher delegates to this native IDEA installation. Record its build and file stamps.
            if (Files.readString(launcher).contains("MES_IDEA_HOME")) {
                Path home = Path.of(System.getenv().getOrDefault("MES_IDEA_HOME", "/Applications/IntelliJ IDEA.app"));
                if (!Files.isDirectory(home)) return null;
                try (var files = Files.walk(home)) {
                    for (Path file : files.filter(Files::isRegularFile).sorted().toList()) {
                        String name = file.getFileName().toString();
                        if (name.equals("build.txt") || name.equals("product-info.json") || name.equals("format.sh")) {
                            identity.append(file).append(Files.readString(file));
                        } else if (name.endsWith(".jar")) {
                            BasicFileAttributes attributes = Files.readAttributes(file, BasicFileAttributes.class);
                            identity.append(file).append(':').append(attributes.size()).append(':')
                                    .append(attributes.lastModifiedTime()).append(':')
                                    .append(attributes.creationTime());
                        }
                    }
                }
            }
            identity.append(System.getProperty("java.version")).append(System.getenv("JAVA_HOME"));
            return hash(identity.toString());
        } catch (IOException | RuntimeException failure) {
            LOG.warn("Cannot validate Java formatter cache; using native formatter", failure);
            return null;
        }
    }

    private Path entry(String identity, Artifact artifact) {
        return cacheDirectory.resolve(hash(identity + "\n" + artifact.relativePath() + "\n" + artifact.content()) + ".cache");
    }

    private String readCached(String identity, Artifact artifact) {
        if (identity == null) return null;
        Path file = entry(identity, artifact);
        try {
            if (!Files.isRegularFile(file) || Files.isSymbolicLink(file) || Files.size(file) > MAX_ENTRY_BYTES)
                return null;
            String stored = Files.readString(file);
            int newline = stored.indexOf('\n');
            if (newline != 64) return null;
            String content = stored.substring(newline + 1);
            return stored.substring(0, newline).equals(hash(content)) ? content : null;
        } catch (IOException failure) {
            LOG.warn("Cannot read Java formatting cache; using native formatter", failure);
            return null;
        }
    }

    private void writeCached(String identity, Artifact artifact, String content) {
        if (identity == null || content.getBytes(StandardCharsets.UTF_8).length + 65 > MAX_ENTRY_BYTES) return;
        Path temporary = null;
        try {
            Files.createDirectories(cacheDirectory);
            if (Files.isSymbolicLink(cacheDirectory)) return;
            temporary = Files.createTempFile(cacheDirectory, "format-", ".tmp");
            Files.writeString(temporary, hash(content) + "\n" + content);
            Files.move(temporary, entry(identity, artifact), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException failure) {
            LOG.warn("Cannot persist Java formatting cache; generation remains successful", failure);
        } finally {
            if (temporary != null) {
                try {
                    Files.deleteIfExists(temporary);
                } catch (IOException failure) {
                    LOG.warn("Cannot clean Java formatting cache temporary file", failure);
                }
            }
        }
    }

    private void pruneCache() {
        if (cacheDirectory == null || !Files.isDirectory(cacheDirectory)) return;
        try (var paths = Files.list(cacheDirectory)) {
            var files = paths.filter(path -> path.getFileName().toString().matches("[a-f0-9]{64}\\.cache"))
                    .sorted(Comparator.comparingLong((Path path) -> path.toFile().lastModified()).reversed()).toList();
            long bytes = 0;
            for (int index = 0; index < files.size(); index++) {
                Path file = files.get(index);
                bytes += Files.size(file);
                if (index >= MAX_CACHE_ENTRIES || bytes > MAX_CACHE_BYTES) Files.deleteIfExists(file);
            }
        } catch (IOException failure) {
            LOG.warn("Cannot prune Java formatting cache", failure);
        }
    }

    private static String hash(String text) {
        return ScaffoldWriter.hash(text.getBytes(StandardCharsets.UTF_8));
    }

}
