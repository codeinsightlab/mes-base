package dev.mesbase.server;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.pagehelper.PageHelper;
import dev.mesbase.core.factory.FactoryId;
import dev.mesbase.core.factory.FactoryScope;
import dev.mesbase.framework.openapi.OpenApiHttp;
import dev.mesbase.framework.openapi.OpenApiRequestFilter;
import dev.mesbase.platform.execution.FactoryContext;
import dev.mesbase.platform.web.RequestBoundaryFilter;
import jakarta.servlet.DispatcherType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * TEST ONLY: consecutive dispatches execute on the same test thread.
 */
class RequestStateLifecycleTest {
    @AfterEach
    void isolateTestRunner() {
        FactoryContext.clear();
        PageHelper.clearPage();
        MDC.remove("requestId");
    }

    @Test
    void paginationIsClearedWhenFailurePrecedesMapperAndNextRequestStartsClean() throws Exception {
        var boundary = new RequestBoundaryFilter();
        assertThrows(IllegalStateException.class, () -> boundary.doFilter(new MockHttpServletRequest(),
                new MockHttpServletResponse(), (request, response) -> {
                    PageHelper.startPage(2, 10);
                    throw new IllegalStateException("TEST ONLY failure before Mapper");
                }));
        assertNull(PageHelper.getLocalPage());
        boundary.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(),
                (request, response) -> assertNull(PageHelper.getLocalPage()));
    }

    @Test
    void asyncAbnormalExitDoesNotLeakFactoryIntoNextDispatch() throws Exception {
        var boundary = new RequestBoundaryFilter();
        var first = new MockHttpServletRequest();
        first.setDispatcherType(DispatcherType.ASYNC);
        assertThrows(IllegalStateException.class, () -> boundary.doFilter(first,
                new MockHttpServletResponse(), (request, response) -> {
                    FactoryContext.bind(new FactoryScope(new FactoryId("TEST_ONLY_A")));
                    throw new IllegalStateException("TEST ONLY async failure");
                }));
        assertTrue(FactoryContext.current().isEmpty());
        var next = new MockHttpServletRequest();
        next.setDispatcherType(DispatcherType.ASYNC);
        boundary.doFilter(next, new MockHttpServletResponse(),
                (request, response) -> assertTrue(FactoryContext.current().isEmpty()));
    }

    @Test
    void openApiErrorUsesBoundaryIdAndRestoresCallerMdc() throws Exception {
        var request = new MockHttpServletRequest("GET", "/open-api/TEST_ONLY");
        var response = new MockHttpServletResponse();
        var json = new ObjectMapper();
        var openApi = new OpenApiRequestFilter(new OpenApiHttp(json));
        MDC.put("requestId", "TEST_ONLY_outer");
        new RequestBoundaryFilter().doFilter(request, response,
                (req, res) -> openApi.doFilter(req, res, (innerRequest, innerResponse) -> {
                    assertEquals(request.getAttribute("mes.requestId"), MDC.get("requestId"));
                    throw new IllegalStateException("TEST ONLY internal failure");
                }));
        assertEquals(500, response.getStatus());
        assertEquals(request.getAttribute("mes.requestId"), response.getHeader("X-Request-Id"));
        assertEquals(response.getHeader("X-Request-Id"), json.readTree(response.getContentAsString()).get("requestId")
                .asText());
        assertEquals("TEST_ONLY_outer", MDC.get("requestId"));
    }
}
