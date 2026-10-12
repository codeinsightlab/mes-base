package dev.mesbase.web.controller.openapi;

import com.fasterxml.jackson.annotation.JsonInclude;
import dev.mesbase.common.openapi.OpenApiEndpoint;
import dev.mesbase.common.openapi.OpenApiError;
import dev.mesbase.framework.openapi.OpenApiDocumentResponse;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.examples.Example;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.security.SecurityScheme;
import java.util.Arrays;
import java.util.Set;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.web.method.HandlerMethod;

final class OpenApiDocumentationContract {
    private OpenApiDocumentationContract() {
    }

    static Operation operation(Operation operation, HandlerMethod handler) {
        var endpoint = AnnotatedElementUtils.findMergedAnnotation(handler.getMethod(), OpenApiEndpoint.class);
        if (endpoint != null) {
            operation.addExtension("x-api-code", endpoint.value());
            if (operation.getDescription() == null) operation.setDescription(endpoint.description());
        }
        for (var error : OpenApiError.values()) {
            String status = String.valueOf(error.getStatus());
            ApiResponse response = operation.getResponses().get(status);
            if (response == null) {
                response = new ApiResponse().description("").content(new Content().addMediaType("application/json",
                        new MediaType().schema(new Schema<>().$ref("#/components/schemas/ErrorResponse"))));
                operation.getResponses().addApiResponse(status, response);
            }
            if (response.getContent() == null) response.setContent(new Content());
            if (response.getContent().get("application/json") == null)
                response.getContent().addMediaType("application/json", new MediaType()
                        .schema(new Schema<>().$ref("#/components/schemas/ErrorResponse")));
            response.getContent().get("application/json").addExamples(error.getCode(), new Example()
                    .value(new ErrorExample(error.getStatus(), error.getName(), null, error.getCode(), "doc-example-request-id")));
            String previous = response.getDescription();
            response.setDescription((previous == null || previous.isEmpty() ? "" : previous + "；")
                    + error.getCode() + "：" + error.getName());
        }
        return operation;
    }

    static void customize(OpenAPI api) {
        api.setInfo(new Info().title("MES Base OpenAPI").version("v1")
                .description("合作应用接口契约。接口可见不代表已获得调用授权。"));
        if (api.getComponents() == null) api.setComponents(new Components());
        api.getComponents().addSecuritySchemes("OpenApiBearer", new SecurityScheme()
                .type(SecurityScheme.Type.HTTP).scheme("bearer")
                .description("Authorization: Bearer {access_token}；Bearer 后必须有一个空格。"));
        ModelConverters.getInstance().readAll(OpenApiDocumentResponse.ErrorResponse.class)
                .forEach(api.getComponents()::addSchemas);
        Schema<?> error = api.getComponents().getSchemas().get("ErrorResponse");
        if (error != null && error.getProperties() != null) {
            for (String field : new String[]{"code", "msg"}) {
                Schema<?> property = error.getProperties().get(field);
                if (property != null) {
                    property.setExample(null);
                    property.setExampleSetFlag(false);
                }
            }
            Schema<?> data = new Schema<>().description("错误响应无数据，固定为 null");
            data.setType("null");
            data.setTypes(Set.of("null"));
            error.getProperties().put("data", data);
        }
        api.addExtension("x-error-codes", Arrays.stream(OpenApiError.values()).map(ErrorDescription::new).toList());
    }

    @JsonInclude(JsonInclude.Include.ALWAYS)
    record ErrorExample(int code, String msg, Object data, String errorCode, String requestId) {
    }

    record ErrorDescription(String code, int httpStatus, String description) {
        ErrorDescription(OpenApiError error) {
            this(error.getCode(), error.getStatus(), error.getName());
        }
    }
}
