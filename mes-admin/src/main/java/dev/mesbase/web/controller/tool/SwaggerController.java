package dev.mesbase.web.controller.tool;

import dev.mesbase.platform.web.EndpointAccess;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Locale;
import org.springdoc.core.customizers.SpringDocCustomizers;
import org.springdoc.core.properties.SpringDocConfigProperties;
import org.springdoc.core.providers.SpringDocProviders;
import org.springdoc.core.service.AbstractRequestService;
import org.springdoc.core.service.GenericResponseService;
import org.springdoc.core.service.OpenAPIService;
import org.springdoc.core.service.OperationService;
import org.springdoc.webmvc.api.OpenApiWebMvcResource;
import org.springframework.beans.factory.ObjectFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * SOURCE_PRODUCT_PORT: Source Swagger entry and tool:swagger:view permission.
 * Springdoc supplies schema generation; only this explicitly classified HTTP entry is registered.
 * The SDK resource is a delegate, not a Spring MVC bean, so it cannot expose unclassified routes.
 */
@RestController
@ConditionalOnProperty(name = "mes.system.enabled", havingValue = "true")
public class SwaggerController {
    private final OpenApiWebMvcResource documents;

    public SwaggerController(ObjectFactory<OpenAPIService> builder, AbstractRequestService requests,
            GenericResponseService responses, OperationService operations,
            SpringDocConfigProperties settings, SpringDocProviders providers,
            SpringDocCustomizers customizers) {
        documents = new OpenApiWebMvcResource(builder, requests, responses, operations,
                settings, providers, customizers);
    }

    @GetMapping(value = "/api/tool/swagger/json", produces = MediaType.APPLICATION_JSON_VALUE)
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED, permission = "tool:swagger:view",
            permissionScope = EndpointAccess.PermissionScope.PLATFORM)
    public byte[] document(HttpServletRequest request, Locale locale)
            throws com.fasterxml.jackson.core.JsonProcessingException {
        return documents.openapiJson(request, "/api/tool/swagger/json", locale);
    }
}
