package dev.mesbase.web.controller.openapi;

import dev.mesbase.common.openapi.OpenApiEndpoint;
import dev.mesbase.framework.openapi.OpenApiTokenController;
import dev.mesbase.platform.web.EndpointAccess;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
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
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import com.fasterxml.jackson.core.JsonProcessingException;

/**
 * Source documentation UI with a dedicated, external-only Springdoc delegate.
 */
@RestController
@ConditionalOnProperty(name = "open-api.enabled", havingValue = "true")
public class OpenApiDocumentationController {
    private final OpenApiWebMvcResource documents;

    public OpenApiDocumentationController(ObjectFactory<OpenAPIService> builder, AbstractRequestService requests,
                                          GenericResponseService responses, OperationService operations,
                                          SpringDocConfigProperties settings, SpringDocProviders providers) {
        var customizers = new SpringDocCustomizers(
                Optional.of(Set.of(OpenApiDocumentationContract::customize)),
                Optional.of(Set.of(OpenApiDocumentationContract::operation)), Optional.empty(),
                Optional.of(Set.of(method -> {
                    var endpoint = AnnotatedElementUtils.findMergedAnnotation(method, OpenApiEndpoint.class);
                    return method.getDeclaringClass() == OpenApiTokenController.class
                            || (endpoint != null && endpoint.value().matches("[a-z][a-z0-9.:_-]{0,99}"));
                })), Optional.empty(), Optional.empty());
        documents = new OpenApiWebMvcResource("open-api", builder, requests, responses, operations,
                settings, providers, customizers) {
            @Override
            protected boolean isPathToMatch(String path) {
                return path.startsWith("/open-api/") && super.isPathToMatch(path);
            }
        };
    }

    @EndpointAccess(EndpointAccess.Kind.PUBLIC)
    @GetMapping({"/openapi-docs/", "/openapi-docs/index.html", "/openapi-docs/app.js", "/openapi-docs/style.css", "/openapi-docs/logo.svg"})
    public ResponseEntity<Resource> asset(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        String name = path.substring("/openapi-docs/".length());
        if (name.isEmpty()) name = "index.html";
        MediaType type = switch (name) {
            case "index.html" -> MediaType.TEXT_HTML;
            case "app.js" -> MediaType.parseMediaType("application/javascript");
            case "style.css" -> MediaType.parseMediaType("text/css");
            case "logo.svg" -> MediaType.parseMediaType("image/svg+xml");
            default -> throw new IllegalArgumentException("Unregistered documentation asset");
        };
        return ResponseEntity.ok().contentType(type).body(new ClassPathResource("static/openapi-docs/" + name));
    }

    @EndpointAccess(EndpointAccess.Kind.PUBLIC)
    @GetMapping(value = "/v3/api-docs/open-api", produces = MediaType.APPLICATION_JSON_VALUE)
    public byte[] contract(HttpServletRequest request, Locale locale) throws JsonProcessingException {
        return documents.openapiJson(request, "/v3/api-docs/open-api", locale);
    }
}
