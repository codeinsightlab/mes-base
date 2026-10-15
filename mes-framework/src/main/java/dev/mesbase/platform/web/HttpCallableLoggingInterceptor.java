package dev.mesbase.platform.web;

import java.util.Map;
import java.util.concurrent.Callable;
import org.slf4j.MDC;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.async.CallableProcessingInterceptor;

/**
 * Only the server-owned HTTP requestId crosses the MVC Callable boundary.
 */
public final class HttpCallableLoggingInterceptor implements CallableProcessingInterceptor {
    private final ThreadLocal<Snapshot> previous = new ThreadLocal<>();

    @Override
    public <T> void preProcess(NativeWebRequest request, Callable<T> task) {
        previous.set(new Snapshot(MDC.getCopyOfContextMap()));
        Object id = request.getAttribute(RequestBoundaryFilter.REQUEST_ID, RequestAttributes.SCOPE_REQUEST);
        if (id instanceof String value) MDC.put("requestId", value);
    }

    @Override
    public <T> void postProcess(NativeWebRequest request, Callable<T> task, Object concurrentResult) {
        Snapshot snapshot = previous.get();
        previous.remove();
        if (snapshot != null) RequestBoundaryFilter.restoreMdc(snapshot.mdc());
    }

    private record Snapshot(Map<String, String> mdc) {
    }
}
