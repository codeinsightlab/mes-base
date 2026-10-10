package dev.mesbase.openapi;

import io.swagger.v3.oas.models.Operation;
import org.junit.jupiter.api.Test;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TEST ONLY: MVC metadata, no business database, authentication or real HTTP invocation.
 */
class OpenApiInfrastructureTest {
    private final WebApplicationContextRunner runner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(OpenApiAutoConfiguration.class))
            .withUserConfiguration(MvcFixture.class);

    @Configuration(proxyBeanMethods = false)
    static class MvcFixture {
        @Bean
        RequestMappingHandlerMapping requestMappingHandlerMapping() {
            return new RequestMappingHandlerMapping();
        }
    }

    @OpenApiEndpoint(description = "TEST ONLY group")
    static class TestHandlers {
        @OpenApiEndpoint(value = "fixture.read", description = "TEST ONLY description")
        public String read() {
            return "TEST ONLY";
        }

        @OpenApiEndpoint("INVALID")
        public void invalid() {
        }

        public void unmarked() {
        }
    }

    @Test
    void dependencyAloneAndExplicitDisableInstallNothing() {
        runner.run(context -> {
            assertThat(context).doesNotHaveBean(OpenApiCatalogService.class);
            assertThat(context).doesNotHaveBean(GroupedOpenApi.class);
            assertThat(context).doesNotHaveBean(OperationCustomizer.class);
        });
        runner.withPropertyValues("open-api.enabled=false").run(context ->
                assertThat(context).doesNotHaveBean(OpenApiCatalogService.class));
    }

    @Test
    void enabledModuleWorksWithoutSystemAndDiscoversLiveMappings() {
        runner.withPropertyValues("open-api.enabled=true", "mes.system.enabled=false").run(context -> {
            assertThat(context).hasSingleBean(OpenApiCatalogService.class);
            assertThat(context).doesNotHaveBean(GroupedOpenApi.class);
            assertThat(context).hasSingleBean(OperationCustomizer.class);
            var mappings = context.getBean(RequestMappingHandlerMapping.class);
            var catalog = context.getBean(OpenApiCatalogService.class);
            assertThat(catalog.list()).isEmpty();
            var handler = new TestHandlers();
            var read = TestHandlers.class.getMethod("read");
            var info = RequestMappingInfo.paths("/open-api/v1/fixture").methods(RequestMethod.GET).build();
            mappings.registerMapping(info, handler, read);
            mappings.registerMapping(RequestMappingInfo.paths("/api/internal").build(), handler, read);
            mappings.registerMapping(RequestMappingInfo.paths("/open-api/v1/invalid").build(), handler,
                    TestHandlers.class.getMethod("invalid"));
            mappings.registerMapping(RequestMappingInfo.paths("/open-api/v1/unmarked").build(), handler,
                    TestHandlers.class.getMethod("unmarked"));
            assertThat(catalog.list()).hasSize(1);
            var api = catalog.list().getFirst();
            assertThat(api.getApiCode()).isEqualTo("fixture.read");
            assertThat(api.getMappings()).hasSize(1);
            var mapping = api.getMappings().getFirst();
            assertThat(mapping.getPath()).isEqualTo("/open-api/v1/fixture");
            assertThat(mapping.getHttpMethod()).isEqualTo("GET");
            assertThat(mapping.getGroupName()).isEqualTo("TEST ONLY group");
            var operation = context.getBean(OperationCustomizer.class).customize(new Operation(),
                    new HandlerMethod(handler, read));
            assertThat(operation.getExtensions()).containsEntry("x-api-code", "fixture.read");
            assertThat(operation.getDescription()).isEqualTo("TEST ONLY description");
            mappings.unregisterMapping(info);
            assertThat(catalog.list()).isEmpty();
        });
    }
}
