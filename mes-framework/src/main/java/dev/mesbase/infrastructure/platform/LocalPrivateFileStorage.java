package dev.mesbase.infrastructure.platform;

import java.nio.file.*;
import java.io.*;

import dev.mesbase.platform.management.FileStorage;
import dev.mesbase.core.error.*;

/**
 * Files addressed by opaque UUID only; symlink traversal rejected at every ancestor.
 */
public final class LocalPrivateFileStorage implements FileStorage {
    private final Path root;

    public LocalPrivateFileStorage(Path root) {
        this.root = root.toAbsolutePath().normalize();
    }

    private Path path(String id) {
        if (!id.matches("[0-9a-f-]{36}")) throw new FoundationException(ErrorCode.NOT_FOUND);
        Path path = root.resolve(id);
        for (Path p = path; p != null; p = p.getParent())
            if (Files.isSymbolicLink(p)) throw new FoundationException(ErrorCode.FORBIDDEN);
        return path;
    }

    public long write(String id, InputStream input, long maxBytes) throws IOException {
        Path p = path(id);
        Files.createDirectories(root);
        long size = 0;
        try (OutputStream out = Files.newOutputStream(p, StandardOpenOption.CREATE_NEW)) {
            byte[] buffer = new byte[8192];
            for (int n; (n = input.read(buffer)) != -1; ) {
                size += n;
                if (size > maxBytes) throw new FoundationException(ErrorCode.UNPROCESSABLE);
                out.write(buffer, 0, n);
            }
            return size;
        } catch (IOException | RuntimeException failure) {
            Files.deleteIfExists(p);
            throw failure;
        }
    }

    public byte[] read(String id, long expectedSize) throws IOException {
        Path p = path(id);
        if (!Files.isRegularFile(p, LinkOption.NOFOLLOW_LINKS) || Files.size(p) != expectedSize)
            throw new FoundationException(ErrorCode.NOT_FOUND);
        return Files.readAllBytes(p);
    }

    public void delete(String id) throws IOException {
        Files.deleteIfExists(path(id));
    }
}
