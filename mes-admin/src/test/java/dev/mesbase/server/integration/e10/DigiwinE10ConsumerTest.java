package dev.mesbase.server.integration.e10;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.lixin.capability.digiwin.e10.E10Exception;
import com.lixin.capability.digiwin.e10.E10Host;
import com.lixin.capability.digiwin.e10.E10OapiClient;
import com.lixin.capability.digiwin.e10.E10Service;
import com.lixin.capability.digiwin.e10.request.E10QueryRequest;
import com.sun.net.httpserver.HttpServer;
import dev.mesbase.core.error.FoundationException;
import dev.mesbase.core.factory.FactoryId;
import dev.mesbase.core.factory.FactoryRuntimeMode;
import dev.mesbase.platform.execution.FactoryIntegrationContext;
import dev.mesbase.platform.execution.FactoryIntegrationSnapshot;
import dev.mesbase.server.managed.FactoryIntegrationRegistry;
import dev.mesbase.system.factory.ManagedFactory;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 仅回环 HTTP：不加载应用、数据库、Redis、定时任务或任何真实 ERP 配置。
 */
class DigiwinE10ConsumerTest {
    private static final String PREFIX = "mes.integration.digiwin-e10.connections.test-e10.";
    private static final ObjectMapper JSON = new ObjectMapper();
    private final AtomicInteger calls = new AtomicInteger();
    private final AtomicReference<JsonNode> parameter = new AtomicReference<>();
    private final AtomicReference<JsonNode> service = new AtomicReference<>();
    private final AtomicReference<JsonNode> host = new AtomicReference<>();
    private HttpServer server;
    private String endpoint;
    private volatile int status = 200;
    private volatile long delayMillis;
    private volatile String response = """
            {"std_data":{"execution":{"code":"0"},"parameter":{"rows":[
            {"doc_no":"TEST_ONLY_MO_01","plan_qty":12.500000}],"cnt":1}}}
            """;

    @Configuration(proxyBeanMethods = false)
    static class RegistryConfiguration {
        @Bean
        FactoryIntegrationRegistry registry(Environment environment) {
            return new FactoryIntegrationRegistry(environment);
        }
    }

    @BeforeEach
    void startLoopbackFixture() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/oapi", exchange -> {
            calls.incrementAndGet();
            try {
                parameter.set(JSON.readTree(exchange.getRequestBody()).path("std_data").path("parameter"));
                service.set(JSON.readTree(exchange.getRequestHeaders().getFirst("digi-service")));
                host.set(JSON.readTree(exchange.getRequestHeaders().getFirst("digi-host")));
                if (delayMillis > 0) Thread.sleep(delayMillis);
                byte[] body = response.getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(status, body.length);
                exchange.getResponseBody().write(body);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            } finally {
                exchange.close();
            }
        });
        server.start();
        endpoint = "http://127.0.0.1:" + server.getAddress().getPort() + "/oapi";
    }

    @AfterEach
    void stopLoopbackFixture() {
        server.stop(0);
    }

    @Test
    void optInConfigurationQueriesTypedRowsWithExactIdentityAndClosesClients() {
        var holder = new AtomicReference<DigiwinE10MoQueries>();
        var snapshot = new AtomicReference<FactoryIntegrationSnapshot>();
        runner(settings()).run(context -> {
            assertNull(context.getStartupFailure());
            assertEquals(0, calls.get(), "装配客户端不能探测 ERP");
            var queries = context.getBean(DigiwinE10MoQueries.class);
            holder.set(queries);
            var registry = context.getBean(FactoryIntegrationRegistry.class);
            snapshot.set(registry.snapshot(new ManagedFactory("TEST_A", "T", "TEST_A", "TEST", null,
                    "ENABLED", 1, "ERP", DigiwinE10MoQueries.PROVIDER, "test-e10", 2, true)));
            FactoryIntegrationContext.run(snapshot.get(), () -> {
                var result = queries.queryMo(request());
                assertEquals("TEST_ONLY_MO_01", result.getRows().getFirst().getDocNo());
                assertEquals(new BigDecimal("12.500000"), result.getRows().getFirst().getPlanQty());
                assertEquals(1, result.getCnt().asInt());
                queries.queryMo(request());
            });
            assertEquals(2, calls.get());
            assertEquals(1, parameter.get().path("page_no").asInt());
            assertEquals(20, parameter.get().path("page_size").asInt());
            assertFalse(parameter.get().path("is_get_schema").asBoolean());
            assertTrue(parameter.get().path("is_get_count").asBoolean());
            assertTrue(parameter.get().path("conditions").isArray());
            assertTrue(parameter.get().path("orders").isArray());
            assertEquals("TEST_ONLY_FINAL_SERVICE_ID", service.get().path("id").asText());
            assertEquals("", host.get().path("id").asText());
        });
        FactoryIntegrationContext.run(snapshot.get(), () -> {
            var failure = assertThrows(E10Exception.class, () -> holder.get().queryMo(request()));
            assertEquals(E10Exception.Reason.CLOSED, failure.getReason());
            assertEquals(E10Exception.Outcome.NOT_SENT, failure.getOutcome());
        });
        holder.get().close();
        assertEquals(2, calls.get(), "关闭客户端后不能发送请求");
    }

    @ParameterizedTest
    @ValueSource(strings = {"endpoint", "host.ver", "host.prod", "host.timezone", "host.ip", "host.id",
            "host.lang", "host.acct", "service.prod", "service.ip", "service.id"})
    void missingExplicitIdentityOrEndpointFailsBeforeAnyNetworkCall(String name) {
        var settings = settings();
        settings.remove(PREFIX + name);
        runner(settings).run(context -> assertNotNull(context.getStartupFailure()));
        assertEquals(0, calls.get());
    }

    @ParameterizedTest
    @ValueSource(strings = {"endpoint", "host.ver", "host.prod", "host.timezone", "host.ip",
            "host.lang", "host.acct", "service.prod", "service.ip", "service.id"})
    void blankRequiredValuesAreRejectedWithoutSending(String name) {
        var values = settings();
        values.put(PREFIX + name, "");
        runner(values).run(context -> assertNotNull(context.getStartupFailure()));
        assertEquals(0, calls.get());
    }

    @ParameterizedTest
    @ValueSource(strings = {"factory-id", "provider", "enabled", "service-url", "revision", "credential-reference"})
    void refusesInvalidOrMismatchedExistingConnectionRegistration(String name) {
        var values = settings();
        values.put("mes.integration.connections.test-e10." + name, switch (name) {
            case "provider" -> "OTHER";
            case "enabled" -> "false";
            case "service-url" -> endpoint + "/other";
            case "revision" -> "0";
            default -> "";
        });
        runner(values).run(context -> assertNotNull(context.getStartupFailure()));
        assertEquals(0, calls.get());
    }

    @Test
    void legitimateEmptyQueryRemainsSuccessfulAndDoesNotInventCount() {
        response = "{\"std_data\":{\"execution\":{\"code\":\"0\"},\"parameter\":{\"rows\":[]}}}";
        runner(settings()).run(context -> FactoryIntegrationContext.run(validSnapshot(), () -> {
            var result = context.getBean(DigiwinE10MoQueries.class).queryMo(request());
            assertTrue(result.getRows().isEmpty());
            assertTrue(result.getCnt().isMissingNode());
        }));
        assertEquals(1, calls.get());
    }

    @Test
    void disabledByDefaultAndEnabledWithoutConnectionsFailsClearly() {
        new ApplicationContextRunner().withUserConfiguration(DigiwinE10Configuration.class).run(context -> {
            assertNull(context.getStartupFailure());
            assertFalse(context.containsBean("digiwinE10MoQueries"));
        });
        runner(Map.of("mes.integration.digiwin-e10.enabled", "true"))
                .run(context -> assertNotNull(context.getStartupFailure()));
        assertEquals(0, calls.get());
    }

    @Test
    void rejectsUnadmittedLocalOtherFactoryOtherProviderAndStaleBindingWithoutSending() {
        runner(settings()).run(context -> {
            var queries = context.getBean(DigiwinE10MoQueries.class);
            assertThrows(FoundationException.class, () -> queries.queryMo(request()));
            for (var snapshot : List.of(
                    new FactoryIntegrationSnapshot(new FactoryId("TEST_A"), FactoryRuntimeMode.LOCAL, null, null, 1, null),
                    snapshot("TEST_B", DigiwinE10MoQueries.PROVIDER, "test-e10", 3),
                    snapshot("TEST_A", "OTHER", "test-e10", 3),
                    snapshot("TEST_A", DigiwinE10MoQueries.PROVIDER, "other", 3),
                    snapshot("TEST_A", DigiwinE10MoQueries.PROVIDER, "test-e10", 4))) {
                FactoryIntegrationContext.run(snapshot,
                        () -> assertThrows(FoundationException.class, () -> queries.queryMo(request())));
            }
            FactoryIntegrationContext.run(validSnapshot(), () -> assertThrows(IllegalArgumentException.class,
                    () -> queries.queryMo(new E10QueryRequest())));
        });
        assertEquals(0, calls.get());
    }

    @Test
    void partialFailureRetainsFullResponseAndIsNeverConvertedToEmptySuccessOrRetried() {
        response = """
                {"std_data":{"execution":{"code":"0"},"parameter":{"rows":[],"proof":"TEST_ONLY",
                "result":{"success":[{"doc_no":"TEST_OK"}],"error":[{"message":"TEST_ERROR","data":{"id":"TEST_BAD"}}]}}}}
                """;
        runner(settings()).run(context -> FactoryIntegrationContext.run(validSnapshot(), () -> {
            var failure = assertThrows(E10Exception.class,
                    () -> context.getBean(DigiwinE10MoQueries.class).queryMo(request()));
            assertEquals(E10Exception.Reason.RESULT_ERROR, failure.getReason());
            assertEquals(E10Exception.Outcome.RESPONSE_RECEIVED, failure.getOutcome());
            assertNotNull(failure.getResponse());
            assertEquals("TEST_ONLY", failure.getResponse().getParameter().path("proof").asText());
            assertEquals(1, failure.getResponse().getParameter().path("result").path("success").size());
            assertEquals(1, failure.getResponse().getParameter().path("result").path("error").size());
            assertEquals(response, failure.getRawResponse());
        }));
        assertEquals(1, calls.get());
    }

    @Test
    void httpFailurePropagatesWithReasonOutcomeAndCompleteBodyWithoutRetry() {
        status = 503;
        response = "TEST_ONLY_SERVICE_UNAVAILABLE";
        runner(settings()).run(context -> FactoryIntegrationContext.run(validSnapshot(), () -> {
            var failure = assertThrows(E10Exception.class,
                    () -> context.getBean(DigiwinE10MoQueries.class).queryMo(request()));
            assertEquals(E10Exception.Reason.HTTP_STATUS, failure.getReason());
            assertEquals(503, failure.getHttpStatus());
            assertEquals(response, failure.getRawResponse());
        }));
        assertEquals(1, calls.get());
    }

    @Test
    void timeoutPreservesUnknownAndDoesNotRetry() {
        delayMillis = 250;
        try (var client = E10OapiClient.builder().endpoint(endpoint)
                .host(new E10Host("TEST", "TEST", "+8", "TEST", "", "TEST", "TEST"))
                .service(new E10Service("TEST", "TEST", "TEST"))
                .responseTimeoutMillis(60).requestTimeoutMillis(100).build();
             var queries = new DigiwinE10MoQueries(Map.of("test-e10",
                     new DigiwinE10MoQueries.Binding("TEST_A", 3, client)))) {
            FactoryIntegrationContext.run(validSnapshot(), () -> {
                var failure = assertThrows(E10Exception.class, () -> queries.queryMo(request()));
                assertEquals(E10Exception.Reason.TIMEOUT, failure.getReason());
                assertEquals(E10Exception.Outcome.UNKNOWN, failure.getOutcome());
            });
            assertEquals(1, calls.get());
        }
    }

    private ApplicationContextRunner runner(Map<String, String> properties) {
        return new ApplicationContextRunner()
                .withUserConfiguration(RegistryConfiguration.class, DigiwinE10Configuration.class)
                .withPropertyValues(properties.entrySet().stream().map(e -> e.getKey() + "=" + e.getValue())
                        .toArray(String[]::new));
    }

    private Map<String, String> settings() {
        var values = new LinkedHashMap<String, String>();
        values.put("mes.integration.digiwin-e10.enabled", "true");
        String registered = "mes.integration.connections.test-e10.";
        values.put(registered + "factory-id", "TEST_A");
        values.put(registered + "provider", DigiwinE10MoQueries.PROVIDER);
        values.put(registered + "service-url", endpoint);
        values.put(registered + "credential-reference", "test-only.credential");
        values.put("test-only.credential", "TEST_ONLY_NOT_SENT");
        values.put(registered + "organization", "TEST_ONLY_ORG");
        values.put(registered + "enabled", "true");
        values.put(registered + "revision", "3");
        values.put(PREFIX + "endpoint", endpoint);
        for (var field : List.of("host.ver", "host.prod", "host.ip", "host.lang", "host.acct", "service.prod", "service.ip")) {
            values.put(PREFIX + field, "TEST_ONLY");
        }
        values.put(PREFIX + "host.timezone", "+8");
        values.put(PREFIX + "host.id", "");
        values.put(PREFIX + "service.id", "TEST_ONLY_FINAL_SERVICE_ID");
        return values;
    }

    private static FactoryIntegrationSnapshot snapshot(String factory, String provider, String reference,
                                                       long revision) {
        return new FactoryIntegrationSnapshot(new FactoryId(factory), FactoryRuntimeMode.ERP, provider, reference, 2, revision);
    }

    private static FactoryIntegrationSnapshot validSnapshot() {
        return snapshot("TEST_A", DigiwinE10MoQueries.PROVIDER, "test-e10", 3);
    }

    private static E10QueryRequest request() {
        var request = new E10QueryRequest();
        request.setPageNo(1);
        request.setPageSize(20);
        request.setIsGetSchema(false);
        request.setIsGetCount(true);
        request.setConditions(JsonNodeFactory.instance.arrayNode());
        request.setOrders(JsonNodeFactory.instance.arrayNode());
        return request;
    }
}
