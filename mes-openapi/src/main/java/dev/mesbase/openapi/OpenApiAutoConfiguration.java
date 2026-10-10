package dev.mesbase.openapi;

import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * No component scan, database, security chain or public HTTP entry is installed.
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnProperty(name = "open-api.enabled", havingValue = "true")
public class OpenApiAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean
    OpenApiCatalogService openApiCatalogService(
            @Lazy @Qualifier("requestMappingHandlerMapping") RequestMappingHandlerMapping handlers) {
        return new OpenApiCatalogService(handlers);
    }

    @Bean
    OperationCustomizer openApiMetadata() {
        return (operation, handler) -> {
            OpenApiEndpoint endpoint = AnnotatedElementUtils.findMergedAnnotation(
                    handler.getMethod(), OpenApiEndpoint.class);
            if (endpoint != null && endpoint.value().matches("[a-z][a-z0-9.:_-]{0,99}")) {
                operation.addExtension("x-api-code", endpoint.value());
                if (operation.getDescription() == null) {
                    operation.setDescription(endpoint.description());
                }
            }
            return operation;
        };
    }

}
