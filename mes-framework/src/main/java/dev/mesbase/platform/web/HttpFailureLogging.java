package dev.mesbase.platform.web;

import dev.mesbase.common.utils.StringUtils;
import dev.mesbase.core.error.FoundationException;
import dev.mesbase.common.exception.ServiceException;
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
            else LOG.error("event=http.request.failed method={} path={} errorCode={}",
                    method(request), path(request), failure.code(), failure);
        } else {
            logBusiness(request, failure, failure.code().name());
        }
    }

    public static void known(HttpServletRequest request, ServiceException failure, String code) {
        if (first(request, failure)) logBusiness(request, failure, code);
    }

    private static void logBusiness(HttpServletRequest request, Throwable failure, String code) {
        if (failure.getCause() == null) {
            LOG.warn("event=http.request.rejected method={} path={} errorCode={} exception={} origin={} throwSite={}",
                    method(request), path(request), code, failure.getClass().getName(), origin(failure), throwSite(failure));
        } else {
            LOG.warn("event=http.request.rejected method={} path={} errorCode={} exception={} origin={} throwSite={} rootCauseOrigin={}",
                    method(request), path(request), code, failure.getClass().getName(), origin(failure), throwSite(failure), rootCauseOrigin(failure));
        }
    }

    private static String origin(Throwable failure) {
        for (StackTraceElement frame : failure.getStackTrace()) {
            if (frame.getClassName().startsWith("dev.mesbase.business.") && valid(frame)) return format(frame);
        }
        return "unknown";
    }

    private static String throwSite(Throwable failure) {
        StackTraceElement[] frames = failure.getStackTrace();
        return frames.length == 0 ? "unknown" : format(frames[0]);
    }

    private static String format(StackTraceElement frame) {
        if (frame.getLineNumber() <= 0 || unavailable(frame.getFileName())
                || unavailable(frame.getClassName()) || unavailable(frame.getMethodName())) return "unknown";
        return "at " + frame.getClassName() + "." + frame.getMethodName()
                + "(" + frame.getFileName() + ":" + frame.getLineNumber() + ")";
    }

    private static boolean valid(StackTraceElement frame) { return !"unknown".equals(format(frame)); }

    private static String method(HttpServletRequest request) { return safe(request.getMethod()); }
    private static String path(HttpServletRequest request) { return safe(request.getRequestURI()); }
    private static String safe(String value) {
        if (value == null || value.codePoints().anyMatch(Character::isISOControl)) return "unknown";
        return value;
    }

    private static boolean unavailable(String value) {
        return StringUtils.isBlank(value) || value.codePoints().anyMatch(Character::isISOControl);
    }

    private static String rootCauseOrigin(Throwable failure) {
        Set<Throwable> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        Throwable root = failure;
        seen.add(root);
        while (root.getCause() != null) {
            root = root.getCause();
            if (!seen.add(root)) return "unknown";
        }
        return origin(root);
    }

    public static void unexpected(HttpServletRequest request, Throwable failure) {
        if (first(request, failure)) {
            if (OpenApiHttp.isOpenApi(request)) logSanitized(request, failure, "INTERNAL_ERROR");
            else LOG.error("event=http.request.failed method={} path={} errorCode=INTERNAL_ERROR", method(request), path(request), failure);
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
