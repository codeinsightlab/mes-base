package dev.mesbase.server;

import dev.mesbase.common.exception.ServiceException;
import dev.mesbase.core.error.ErrorCode;
import dev.mesbase.core.error.FoundationException;

/**
 * TEST ONLY: failures originate in an application package, as they do in real server code.
 */
public final class HttpLoggingBusinessProbe {
    private HttpLoggingBusinessProbe() {
    }

    public static void reject(String kind) {
        switch (kind) {
            case "bad-request" -> throw new FoundationException(ErrorCode.BAD_REQUEST);
            case "unauthenticated" -> throw new FoundationException(ErrorCode.UNAUTHENTICATED);
            case "business-401" -> throw new ServiceException("TEST ONLY expected authentication", 401);
            case "business" -> throw new ServiceException("TEST ONLY expected validation");
            case "known" -> throw new FoundationException(ErrorCode.UNPROCESSABLE);
            default -> {
            }
        }
    }
}
