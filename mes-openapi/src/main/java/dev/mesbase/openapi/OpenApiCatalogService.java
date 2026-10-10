package dev.mesbase.openapi;

import dev.mesbase.openapi.OpenApiEndpoint;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * 只读取当前MVC注册信息，不扫描业务模块、不访问数据库。
 */
public class OpenApiCatalogService {
    private final RequestMappingHandlerMapping handlers;

    public OpenApiCatalogService(
            @Lazy @Qualifier("requestMappingHandlerMapping") RequestMappingHandlerMapping handlers) {
        this.handlers = handlers;
    }

    public List<OpenApiCatalogVo> list() {
        List<OpenApiCatalogVo> result = new ArrayList<>();
        handlers.getHandlerMethods().forEach((mapping, handler) -> collect(result, mapping, handler));
        result.sort(Comparator.comparing(OpenApiCatalogVo::getApiCode));
        result.forEach(api -> api.getMappings()
                .sort(Comparator.comparing(OpenApiMappingVo::getPath).thenComparing(OpenApiMappingVo::getHttpMethod)));
        return result;
    }

    private void collect(List<OpenApiCatalogVo> result, RequestMappingInfo mapping, HandlerMethod handler) {
        OpenApiEndpoint annotation = AnnotatedElementUtils.findMergedAnnotation(handler.getMethod(), OpenApiEndpoint.class);
        if (annotation == null || !annotation.value().matches("[a-z][a-z0-9.:_-]{0,99}")) return;
        Set<String> paths = mapping.getPatternValues();
        for (String path : paths) {
            if (!path.startsWith("/open-api/v1/")) continue;
            OpenApiCatalogVo api = result.stream().filter(item -> item.getApiCode().equals(annotation.value()))
                    .findFirst().orElse(null);
            if (api == null) {
                api = new OpenApiCatalogVo();
                api.setApiCode(annotation.value());
                result.add(api);
            }
            List<String> methods = new ArrayList<>();
            mapping.getMethodsCondition().getMethods().forEach(method -> methods.add(method.name()));
            if (methods.isEmpty()) methods.add("ANY");
            for (String method : methods) {
                boolean present = api.getMappings().stream()
                        .anyMatch(item -> item.getPath().equals(path) && item.getHttpMethod().equals(method));
                if (!present) {
                    OpenApiEndpoint classDoc = AnnotatedElementUtils.findMergedAnnotation(handler.getBeanType(), OpenApiEndpoint.class);
                    OpenApiMappingVo entry = new OpenApiMappingVo(method, path);
                    entry.setGroupId(handler.getBeanType().getName());
                    entry.setGroupName(classDoc == null || classDoc.description().isEmpty()
                            ? handler.getBeanType().getSimpleName() : classDoc.description());
                    entry.setDescription(annotation.description());
                    api.getMappings().add(entry);
                }
            }
        }
    }
}
