package dev.mesbase.server.integration.e10;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.lixin.capability.digiwin.e10.E10Exception;
import com.lixin.capability.digiwin.e10.E10Host;
import com.lixin.capability.digiwin.e10.E10OapiClient;
import com.lixin.capability.digiwin.e10.E10Service;
import com.lixin.capability.digiwin.e10.gateway.OapiMoGateway;
import com.lixin.capability.digiwin.e10.request.E10QueryRequest;
import com.sun.net.httpserver.HttpServer;
import dev.mesbase.core.error.FoundationException;
import dev.mesbase.framework.sync.SyncCoordinator;
import dev.mesbase.core.factory.FactoryId;
import dev.mesbase.core.factory.FactoryRuntimeMode;
import dev.mesbase.platform.execution.FactoryIntegrationContext;
import dev.mesbase.platform.execution.FactoryIntegrationSnapshot;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

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
        runner().run(context -> {
            assertNull(context.getStartupFailure());
            assertEquals(0, calls.get(), "装配客户端不能探测 ERP");
            var queries = context.getBean(DigiwinE10MoQueries.class);
            holder.set(queries);
            snapshot.set(validSnapshot());
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
            assertThrows(IllegalStateException.class, () -> holder.get().queryMo(request()));
        });
        holder.get().close();
        assertEquals(2, calls.get(), "关闭客户端后不能发送请求");
    }

    @Test
    void invalidStoredIdentityAndEndpointFailBeforeAnyNetworkCall() {
        for (var value : List.of(
                new FactoryOapiSettings("https://user:secret@erp.invalid/api", settings().host(), settings().service()),
                new FactoryOapiSettings(endpoint, null, settings().service()),
                new FactoryOapiSettings(endpoint, settings().host(), null))) {
            assertThrows(IllegalArgumentException.class, value::validate);
        }
        assertEquals(0, calls.get());
    }

    @Test
    void legitimateEmptyQueryRemainsSuccessfulAndDoesNotInventCount() {
        response = "{\"std_data\":{\"execution\":{\"code\":\"0\"},\"parameter\":{\"rows\":[]}}}";
        runner().run(context -> FactoryIntegrationContext.run(validSnapshot(), () -> {
            var result = context.getBean(DigiwinE10MoQueries.class).queryMo(request());
            assertTrue(result.getRows().isEmpty());
            assertTrue(result.getCnt().isMissingNode());
        }));
        assertEquals(1, calls.get());
    }

    @Test
    void runtimeDisabledCreatesNoClientAndRuntimeEnabledDoesNotQueryAtStartup() {
        new ApplicationContextRunner().withUserConfiguration(DigiwinE10Configuration.class).run(context -> {
            assertNull(context.getStartupFailure());
            assertFalse(context.containsBean("digiwinE10MoQueries"));
        });
        runner().run(context -> {
            assertNull(context.getStartupFailure());
            assertNotNull(context.getBean(DigiwinE10MoQueries.class));
        });
        assertEquals(0, calls.get());
    }

    @Test
    void rejectsUnadmittedLocalOtherFactoryOtherProviderAndStaleBindingWithoutSending() {
        runner().run(context -> {
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
        runner().run(context -> FactoryIntegrationContext.run(validSnapshot(), () -> {
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
        runner().run(context -> FactoryIntegrationContext.run(validSnapshot(), () -> {
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
                .responseTimeoutMillis(60).requestTimeoutMillis(100).build()) {
            var gateway = new OapiMoGateway(client);
            var failure = assertThrows(E10Exception.class, () -> gateway.mo().query(request()));
            assertEquals(E10Exception.Reason.TIMEOUT, failure.getReason());
            assertEquals(E10Exception.Outcome.UNKNOWN, failure.getOutcome());
            assertEquals(1, calls.get());
        }
    }

    @Test
    void cmsQueryAndBothReadsUseSdkRoutesAndExactLocators() {
        runner().run(context -> FactoryIntegrationContext.run(validSnapshot(), () -> {
            var queries = context.getBean(DigiwinE10MoQueries.class);
            response = "{\"std_data\":{\"execution\":{\"code\":\"0\"},\"parameter\":{\"rows\":[{\"warehouse_no\":\"WH_A\",\"om_site_id\":\"SITE_A\"}]}}}";
            assertEquals("WH_A", queries.queryWarehouse(request()).getRows().getFirst().getWarehouseNo().asText());
            assertEquals("e10.oapi.warehouse.list.data.query.get", service.get().path("name").asText());
            response = "{\"std_data\":{\"execution\":{\"code\":\"0\"},\"parameter\":{\"result\":{\"success\":[{\"doc_no\":\"MO_A\"}],\"error\":[]}}}}";
            assertEquals("MO_A", queries.readMo("MO_A").getRecords().getFirst().getDocNo().asText());
            assertEquals("MO_A", parameter.get().path("data_keys").get(0).path("DOC_NO").asText());
            assertEquals("e10.oapi.wo.details.data.read.get", service.get().path("name").asText());
            assertEquals("Read_0001", parameter.get().path("dataExchangeProjectID").asText());
            response = "{\"std_data\":{\"execution\":{\"code\":\"0\"},\"parameter\":{\"result\":{\"success\":[{\"warehouse_no\":\"WH_A\",\"source_type_key\":\"PLANT\",\"om_site_id\":\"SITE_A\"}],\"error\":[]}}}}";
            assertEquals("WH_A", queries.readWarehouse("WH_A", "PLANT", "SITE_A").getRecords().getFirst()
                    .getWarehouseNo().asText());
            assertEquals("e10.oapi.warehouse.details.data.read.get", service.get().path("name").asText());
            var key = parameter.get().path("data_keys").get(0);
            assertEquals(3, key.size());
            assertEquals("WH_A", key.path("WAREHOUSE_CODE").asText());
            assertEquals("PLANT", key.path("Owner_Org.RTK").asText());
            assertEquals("SITE_A", key.path("Owner_Org_PLANT_PLANT_CODE").asText());
            assertThrows(IllegalArgumentException.class, () -> queries.readWarehouse("WH_A", "", "SITE_A"));
            assertThrows(IllegalArgumentException.class, () -> queries.readMo(""));
            var oversized = request();
            oversized.setPageSize(101);
            assertThrows(IllegalArgumentException.class, () -> queries.queryWarehouse(oversized));
        }));
        assertEquals(3, calls.get());
    }

    @Test
    void actualSdkDecoderPreservesLargeDecimalAndDoesNotInventAnExternalIdentity() {
        response = "{\"std_data\":{\"execution\":{\"code\":\"0\"},\"parameter\":{\"rows\":[{\"doc_no\":\"TEST_ONLY\",\"plan_qty\":999999999999.123456}]}}}";
        runner().run(context -> FactoryIntegrationContext.run(validSnapshot(), () -> {
            var row = context.getBean(DigiwinE10MoQueries.class).queryMo(request()).getRows().getFirst();
            assertEquals(new BigDecimal("999999999999.123456"), row.getPlanQty());
            assertEquals("e10.oapi.wo.list.data.query.get", service.get().path("name").asText());
        }));
    }

    @Test
    void validFactoriesKeepIndependentSdkIdentityAndRejectRetiredConfiguration() {
        var configurations = mock(FactoryOapiService.class);
        var second = new FactoryOapiSettings(endpoint, settings().host(),
                new FactoryOapiSettings.Service("TEST_ONLY", "TEST_ONLY", "TEST_B_SERVICE"));
        when(configurations.connection("TEST_A")).thenReturn(new FactoryOapiService.Connection("TEST_A", "test-e10", 3, settings()));
        when(configurations.connection("TEST_B")).thenReturn(new FactoryOapiService.Connection("TEST_B", "test-e10", 3, second));
        try (var queries = new DigiwinE10MoQueries(configurations)) {
            FactoryIntegrationContext.run(validSnapshot(), () -> queries.queryMo(request()));
            assertEquals("TEST_ONLY_FINAL_SERVICE_ID", service.get().path("id").asText());
            FactoryIntegrationContext.run(snapshot("TEST_B", DigiwinE10MoQueries.PROVIDER, "test-e10", 3),
                    () -> queries.queryWarehouse(request()));
            assertEquals("TEST_B_SERVICE", service.get().path("id").asText());
            when(configurations.connection("TEST_A")).thenReturn(new FactoryOapiService.Connection("TEST_A", "test-e10", 4, settings()));
            FactoryIntegrationContext.run(validSnapshot(), () -> assertThrows(FoundationException.class,
                    () -> queries.queryMo(request())));
        }
        assertEquals(2, calls.get());
    }

    private ApplicationContextRunner runner() {
        var configurations = mock(FactoryOapiService.class);
        when(configurations.connection("TEST_A")).thenReturn(new FactoryOapiService.Connection("TEST_A", "test-e10", 3, settings()));
        return new ApplicationContextRunner()
                .withBean(FactoryOapiService.class, () -> configurations)
                .withBean(SyncCoordinator.class, () -> mock(SyncCoordinator.class))
                .withUserConfiguration(DigiwinE10Configuration.class)
                .withPropertyValues("mes.runtime.enabled=true");
    }

    private FactoryOapiSettings settings() {
        return new FactoryOapiSettings(endpoint,
                new FactoryOapiSettings.Host("TEST_ONLY", "TEST_ONLY", "+8", "TEST_ONLY", "", "TEST_ONLY", "TEST_ONLY"),
                new FactoryOapiSettings.Service("TEST_ONLY", "TEST_ONLY", "TEST_ONLY_FINAL_SERVICE_ID"));
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
