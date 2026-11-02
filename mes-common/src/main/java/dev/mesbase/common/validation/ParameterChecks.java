package dev.mesbase.common.validation;

import dev.mesbase.common.utils.StringUtils;
import dev.mesbase.core.error.ErrorCode;
import dev.mesbase.core.error.FoundationException;
import dev.mesbase.core.error.SafeBusinessMessage;

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
        if (offset < 0 || limit < 1 || limit > maximum)
            reject(ErrorCode.BAD_REQUEST, new SafeBusinessMessage("分页参数无效，请检查起始位置及每页条数"));
    }

    public static void text(String value, boolean required, int maximum, ErrorCode error) {
        if (required && StringUtils.isBlank(value) || value != null && value.length() > maximum) reject(error);
    }

    public static void text(String value, boolean required, int maximum, ErrorCode error,
                            SafeBusinessMessage message) {
        if (required && StringUtils.isBlank(value) || value != null && value.length() > maximum)
            reject(error, message);
    }

    public static void reject(ErrorCode error) {
        throw new FoundationException(error);
    }

    public static void reject(ErrorCode error, SafeBusinessMessage message) {
        throw new FoundationException(error, message);
    }
}
