package dev.mesbase.platform.web;

import java.util.ArrayList;
import java.util.List;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpMethod;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * Only explicitly declared public MVC methods bypass authentication; all other surfaces fail closed.
 */
public final class PublicEndpointMatcher implements RequestMatcher, SmartInitializingSingleton {
    private final ObjectProvider<RequestMappingHandlerMapping> mappings;
    private volatile List<RequestMatcher> publicMethods = List.of();

    public PublicEndpointMatcher(ObjectProvider<RequestMappingHandlerMapping> mappings) {
        this.mappings = mappings;
    }

    @Override
    public void afterSingletonsInstantiated() {
        List<RequestMatcher> result = new ArrayList<>();
        mappings.getObject().getHandlerMethods().forEach((mapping, handler) -> {
            for (String path : mapping.getPatternValues()) {
                if (EndpointCatalog.required(handler, path).value() != EndpointAccess.Kind.PUBLIC) continue;
                var methods = mapping.getMethodsCondition().getMethods();
                if (methods.isEmpty()) result.add(PathPatternRequestMatcher.withDefaults().matcher(path));
                else methods.forEach(method -> result.add(PathPatternRequestMatcher.withDefaults()
                        .matcher(HttpMethod.valueOf(method.name()), path)));
            }
        });
        publicMethods = List.copyOf(result);
    }

    @Override
    public boolean matches(HttpServletRequest request) {
        return publicMethods.stream().anyMatch(matcher -> matcher.matches(request));
    }
}
