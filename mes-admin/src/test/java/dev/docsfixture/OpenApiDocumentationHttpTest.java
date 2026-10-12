package dev.docsfixture;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.MesBaseApplication;
import dev.mesbase.common.openapi.OpenApiEndpoint;
import dev.mesbase.platform.web.EndpointAccess;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * TEST ONLY: actual embedded HTTP/Springdoc, no client credentials or live databases.
 */
@SpringBootTest(classes = {MesBaseApplication.class, OpenApiDocumentationHttpTest.Probe.class},
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"mes.system.enabled=false", "mes.platform.enabled=false", "mes.runtime.enabled=false", "open-api.enabled=true",
                "springdoc.packages-to-scan=dev.docsfixture,dev.mesbase.web.controller"})
class OpenApiDocumentationHttpTest {
    @LocalServerPort
    int port;
    private final HttpClient http = HttpClient.newHttpClient();

    @RestController
    @OpenApiEndpoint(description = "TEST ONLY")
    static class Probe {
        @EndpointAccess(value = EndpointAccess.Kind.AUTHENTICATED, permissionScope = EndpointAccess.PermissionScope.PLATFORM)
        @OpenApiEndpoint(value = "test.read", description = "TEST ONLY external contract")
        @GetMapping("/open-api/v1/test-only/docs")
        String external() {
            return "TEST ONLY";
        }

        @EndpointAccess(EndpointAccess.Kind.PUBLIC)
        @OpenApiEndpoint(value = "test.internal", description = "TEST ONLY mislabelled internal endpoint")
        @GetMapping("/api/test-only/hidden-from-external-docs")
        String internal() {
            return "TEST ONLY";
        }
    }

    private HttpResponse<String> request(String path, String method) throws Exception {
        return http.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .method(method, HttpRequest.BodyPublishers.noBody()).build(), HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void publicReadOnlyAssetsAndExternalOnlyContractAreAvailable() throws Exception {
        var page = request("/openapi-docs/", "GET");
        assertEquals(200, page.statusCode());
        assertTrue(page.body().contains("MES Base"));
        assertFalse(page.body().contains("和日"));
        assertTrue(page.headers().firstValue("Content-Security-Policy").orElseThrow().contains("connect-src 'self'"));
        for (String asset : new String[]{"index.html", "app.js", "style.css", "logo.svg"}) {
            assertEquals(200, request("/openapi-docs/" + asset, "GET").statusCode());
            assertEquals(403, request("/openapi-docs/" + asset, "POST").statusCode());
        }
        assertEquals(403, request("/openapi-docs/missing.js", "GET").statusCode());
        assertEquals(403, request("/v3/api-docs/open-api", "POST").statusCode());
        assertEquals(401, request("/v3/api-docs", "GET").statusCode());
        var response = request("/v3/api-docs/open-api", "GET");
        assertEquals(200, response.statusCode());
        var contract = new ObjectMapper().readTree(response.body());
        assertEquals("MES Base OpenAPI", contract.path("info").path("title").asText());
        assertTrue(contract.path("paths").has("/open-api/v1/test-only/docs"));
        assertFalse(contract.path("paths").has("/api/test-only/hidden-from-external-docs"));
        contract.path("paths").fieldNames().forEachRemaining(path -> assertTrue(path.startsWith("/open-api/"), path));
        assertEquals("test.read", contract.path("paths").path("/open-api/v1/test-only/docs").path("get")
                .path("x-api-code").asText());
        assertTrue(contract.path("x-error-codes").isArray());
        var errorFields = contract.path("components").path("schemas").path("ErrorResponse").path("properties");
        assertFalse(errorFields.path("code").has("example"));
        assertFalse(errorFields.path("msg").has("example"));
        assertEquals("null", errorFields.path("data").path("type").asText());
        assertEquals(401, request("/open-api/v1/test-only/docs", "GET").statusCode());
    }
}
