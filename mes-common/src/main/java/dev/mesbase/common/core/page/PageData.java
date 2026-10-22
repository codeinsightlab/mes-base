package dev.mesbase.common.core.page;

import java.util.List;

/**
 * Pagination query/response shape; independent of the paging plugin and HTTP transport.
 */
public interface PageData<T> {
    List<T> items();

    long total();

    int offset();

    int limit();
}
