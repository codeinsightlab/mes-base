package dev.mesbase.platform.management;

import java.io.*;

public interface FileStorage {
    long write(String id, InputStream input, long maxBytes) throws IOException;

    byte[] read(String id, long expectedSize) throws IOException;

    void delete(String id) throws IOException;
}
