package dev.mesbase.platform.web;

import dev.mesbase.core.error.ErrorCode;
import dev.mesbase.core.error.FoundationException;
import dev.mesbase.core.error.SafeBusinessMessage;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.*;

class FoundationSafeBusinessMessageTest {
    @Test void oldConstructorAndErrorCodeWriterKeepDefaultContract() throws Exception {
        var request = request();
        var failure = new FoundationException(ErrorCode.RESOURCE_CONFLICT);
        assertEquals(ErrorCode.RESOURCE_CONFLICT.safeMessage(), failure.getMessage());
        assertEquals(ErrorCode.RESOURCE_CONFLICT.safeMessage(), ErrorWriter.body(failure, request).message());
        assertEquals(ErrorCode.RESOURCE_CONFLICT.safeMessage(), ErrorWriter.body(ErrorCode.RESOURCE_CONFLICT, request).message());
        var response = new MockHttpServletResponse();
        ErrorWriter.write(ErrorCode.RESOURCE_CONFLICT, request, response);
        assertEquals(409, response.getStatus());
        assertTrue(response.getContentAsString().contains(ErrorCode.RESOURCE_CONFLICT.safeMessage()));
    }

    @Test void customMessageMatchesAdviceAndFilterWriterWithoutChangingContract() throws Exception {
        var request = request();
        var failure = new FoundationException(ErrorCode.RESOURCE_CONFLICT,
                new SafeBusinessMessage("当前工单已关闭，无法继续报工"));
        var advice = new FoundationErrorAdvice().known(failure, request);
        var response = new MockHttpServletResponse();
        ErrorWriter.write(failure, request, response);
        assertEquals(409, advice.getStatusCode().value());
        assertEquals(ErrorCode.RESOURCE_CONFLICT.name(), advice.getBody().errorCode());
        assertEquals("TEST_REQUEST", advice.getBody().requestId());
        assertEquals(advice.getBody().message(), ErrorWriter.body(failure, request).message());
        assertEquals(409, response.getStatus());
        assertTrue(response.getContentAsString().contains(advice.getBody().message()));
    }

    @Test void invalidBusinessMessagesAreRejectedAndUnexpectedFailureRemainsGeneric() {
        assertThrows(IllegalArgumentException.class, () -> new SafeBusinessMessage("  "));
        assertThrows(IllegalArgumentException.class, () -> new SafeBusinessMessage("x".repeat(301)));
        assertThrows(IllegalArgumentException.class, () -> new SafeBusinessMessage("bad\nmessage"));
        var response = new FoundationErrorAdvice().unexpected(new IllegalStateException("SQL password=secret"), request());
        assertEquals(ErrorCode.INTERNAL_ERROR.name(), response.getBody().errorCode());
        assertEquals(ErrorCode.INTERNAL_ERROR.safeMessage(), response.getBody().message());
        assertFalse(response.getBody().message().contains("secret"));
    }

    private static MockHttpServletRequest request() {
        var request = new MockHttpServletRequest("POST", "/api/test");
        request.setAttribute(RequestBoundaryFilter.REQUEST_ID, "TEST_REQUEST");
        return request;
    }
}
