package dev.mesbase.business.directory.domain;

import dev.mesbase.common.core.page.PageData;
import java.util.List;

public record DirectoryPage<T>(List<T> items, long total, int offset, int limit) implements PageData<T> {
}
