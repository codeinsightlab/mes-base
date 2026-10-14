package dev.mesbase.platform.web;

import dev.mesbase.core.error.FoundationException;
import dev.mesbase.framework.openapi.OpenApiHttp;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Logs at the existing failure boundary; does not resolve or change exceptions.
 */
public final class HttpFailureLogging {
    private static final Logger LOG = LoggerFactory.getLogger(HttpFailureLogging.class);
    private static final String SEEN = HttpFailureLogging.class.getName();

    private HttpFailureLogging() {
    }

    public static void known(HttpServletRequest request, FoundationException failure) {
        if (!first(request, failure)) return;
        if (failure.code().httpStatus() >= 500) {
            if (OpenApiHttp.isOpenApi(request)) logSanitized(request, failure, failure.code().name());
            else LOG.error("event=http.request.failed errorCode={}",
                    failure.code(), failure);
        } else {
            LOG.info("event=http.request.rejected errorCode={}",
                    failure.code());
        }
    }

    public static void unexpected(HttpServletRequest request, Throwable failure) {
        if (first(request, failure)) {
            if (OpenApiHttp.isOpenApi(request)) logSanitized(request, failure, "INTERNAL_ERROR");
            else LOG.error("event=http.request.failed errorCode=INTERNAL_ERROR",
                    failure);
        }
    }

    public static void openApi(HttpServletRequest request, Throwable failure, String code) {
        if (first(request, failure)) logSanitized(request, failure, code);
    }

    private static void logSanitized(HttpServletRequest request, Throwable failure, String code) {
        StringBuilder trace = new StringBuilder();
        appendStack(trace, failure, "", Collections.newSetFromMap(new IdentityHashMap<>()));
        LOG.error("event=http.request.failed errorCode={} failureTrace={}",
                code, trace);
    }

    private static void appendStack(StringBuilder trace, Throwable failure, String label, Set<Throwable> seen) {
        if (failure == null || !seen.add(failure)) return;
        trace.append(label).append(failure.getClass().getName());
        for (StackTraceElement frame : failure.getStackTrace()) trace.append(" at ").append(frame);
        for (Throwable suppressed : failure.getSuppressed()) appendStack(trace, suppressed, " suppressed: ", seen);
        appendStack(trace, failure.getCause(), " caused by: ", seen);
    }

    private static boolean first(HttpServletRequest request, Throwable failure) {
        synchronized (request) {
            @SuppressWarnings("unchecked")
            Set<Throwable> seen = (Set<Throwable>) request.getAttribute(SEEN);
            if (seen == null) {
                seen = Collections.newSetFromMap(new IdentityHashMap<>());
                request.setAttribute(SEEN, seen);
            }
            Set<Throwable> chain = Collections.newSetFromMap(new IdentityHashMap<>());
            for (Throwable current = failure; current != null && chain.add(current); current = current.getCause()) {
                if (seen.contains(current)) return false;
            }
            seen.addAll(chain);
            return true;
        }
    }
}
