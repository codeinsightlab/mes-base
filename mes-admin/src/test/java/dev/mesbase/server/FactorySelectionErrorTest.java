package dev.mesbase.server;

import dev.mesbase.core.error.ErrorCode;
import dev.mesbase.platform.web.ErrorWriter;
import dev.mesbase.platform.web.FoundationErrorAdvice;
import dev.mesbase.core.error.FoundationException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FactorySelectionErrorTest {
    @Test
    void contextFailuresShareRecoveryCodeButPlatformAndBusinessRejectionsDoNot() throws Exception {
        var request = new MockHttpServletRequest("GET", "/api/factory/navigation");
        for (var reason : new ErrorCode[]{ErrorCode.FACTORY_DISABLED, ErrorCode.FACTORY_NOT_FOUND,
                ErrorCode.FACTORY_SCOPE_REQUIRED, ErrorCode.RUNTIME_NOT_CONFIGURED}) {
            var failure = new FoundationException(reason);
            var response = new FoundationErrorAdvice().known(failure, request);
            assertEquals(422, response.getStatusCode().value());
            assertEquals("FACTORY_SELECTION_REQUIRED", response.getBody().errorCode());
            assertEquals(reason, failure.code());
            var filterResponse = new MockHttpServletResponse();
            ErrorWriter.write(reason, request, filterResponse);
            assertEquals(422, filterResponse.getStatus());
            assertTrue(filterResponse.getContentAsString().contains("FACTORY_SELECTION_REQUIRED"));
        }
        assertEquals(ErrorCode.UNPROCESSABLE, ErrorWriter.responseCode(ErrorCode.UNPROCESSABLE, request));
        assertEquals(ErrorCode.FORBIDDEN, ErrorWriter.responseCode(ErrorCode.FORBIDDEN, request));
        assertEquals(ErrorCode.ROUTING_METADATA_UNAVAILABLE,
                ErrorWriter.responseCode(ErrorCode.ROUTING_METADATA_UNAVAILABLE, request));
        request.setRequestURI("/api/system/factory");
        assertEquals(ErrorCode.FACTORY_NOT_FOUND, ErrorWriter.responseCode(ErrorCode.FACTORY_NOT_FOUND, request));
    }
}
