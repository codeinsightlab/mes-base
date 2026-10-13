package dev.mesbase.modules.directory.domain;

import java.util.List;

public record DirectoryPage<T>(List<T> items, long total, int offset, int limit) {
}
