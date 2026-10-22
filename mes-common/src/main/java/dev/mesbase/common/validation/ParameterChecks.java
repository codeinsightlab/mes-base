package dev.mesbase.common.validation;

import dev.mesbase.common.utils.StringUtils;
import dev.mesbase.core.error.ErrorCode;
import dev.mesbase.core.error.FoundationException;

/**
 * Shared parameter bounds. Domain callers retain their explicit error codes.
 */
public final class ParameterChecks {
    private ParameterChecks() {
    }

    public static void paging(int offset, int limit) {
        paging(offset, limit, 100);
    }

    public static void paging(int offset, int limit, int maximum) {
        if (offset < 0 || limit < 1 || limit > maximum) reject(ErrorCode.BAD_REQUEST);
    }

    public static void text(String value, boolean required, int maximum, ErrorCode error) {
        if (required && StringUtils.isBlank(value) || value != null && value.length() > maximum) reject(error);
    }

    public static void reject(ErrorCode error) {
        throw new FoundationException(error);
    }
}
