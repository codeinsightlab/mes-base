package dev.mesbase.platform.web;

import dev.mesbase.platform.execution.FactoryContext;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.ServletWebRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HttpCallableLoggingInterceptorTest {
    @ParameterizedTest
    @CsvSource({"false,false", "false,true", "true,false", "true,true"})
    void reusedWorkerRestoresMdcAfterSuccessOrFailure(boolean previousMdc, boolean failure) throws Exception {
        Map<String, String> caller = MDC.getCopyOfContextMap();
        try (var worker = Executors.newSingleThreadExecutor()) {
            MDC.put("requestId", "TEST_ONLY_CALLER");
            MDC.put("TEST_ONLY_CALLER_SECRET", "must-not-propagate");
            var interceptor = new HttpCallableLoggingInterceptor();
            Map<String, String> baseline = previousMdc ? Map.of("requestId", "TEST_ONLY_BACKGROUND", "job", "TEST_ONLY_JOB") : null;
            worker.submit(() -> {
                RequestBoundaryFilter.restoreMdc(baseline);
                return null;
            }).get(5, TimeUnit.SECONDS);
            for (String id : new String[]{"TEST_ONLY_HTTP_ONE", "TEST_ONLY_HTTP_TWO"}) {
                var servlet = new MockHttpServletRequest();
                servlet.setAttribute(RequestBoundaryFilter.REQUEST_ID, id);
                var request = new ServletWebRequest(servlet);
                Callable<String> task = () -> {
                    assertEquals(id, MDC.get("requestId"));
                    assertNull(MDC.get("TEST_ONLY_CALLER_SECRET"));
                    assertTrue(FactoryContext.current().isEmpty());
                    MDC.put("TEST_ONLY_TASK_MUTATION", "discard");
                    if (failure) throw new IllegalStateException("TEST ONLY callable failure");
                    return "TEST ONLY result";
                };
                worker.submit(() -> {
                    interceptor.preProcess(request, task);
                    Object result;
                    try {
                        result = task.call();
                    } catch (IllegalStateException expected) {
                        result = expected;
                    } finally {
                        // Spring invokes postProcess on the worker, including exception/timeout results.
                        interceptor.postProcess(request, task, null);
                    }
                    assertEquals(failure, result instanceof IllegalStateException);
                    assertEquals(baseline, MDC.getCopyOfContextMap());
                    return null;
                }).get(5, TimeUnit.SECONDS);
            }
            assertEquals("TEST_ONLY_CALLER", MDC.get("requestId"));
        } finally {
            RequestBoundaryFilter.restoreMdc(caller);
        }
    }
}
