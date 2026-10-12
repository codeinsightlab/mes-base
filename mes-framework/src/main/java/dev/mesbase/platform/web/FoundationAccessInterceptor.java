package dev.mesbase.platform.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.method.HandlerMethod;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.authentication.AnonymousAuthenticationToken;

import java.util.Optional;
import java.util.function.Consumer;
import dev.mesbase.core.factory.FactoryScope;
import dev.mesbase.infrastructure.persistence.BusinessRequestSnapshot;

import dev.mesbase.core.factory.FactoryId;
import dev.mesbase.core.security.AuthenticatedActor;
import dev.mesbase.core.security.AuthorizationService;
import dev.mesbase.core.factory.FactorySelectionService;
import dev.mesbase.core.security.Permission;
import dev.mesbase.core.error.ErrorCode;
import dev.mesbase.core.error.FoundationException;
import dev.mesbase.platform.execution.FactoryContext;
import dev.mesbase.common.openapi.OpenApiPrincipal;
import dev.mesbase.framework.openapi.OpenApiHttp;
import dev.mesbase.platform.security.PlatformAuthorization;

public final class FoundationAccessInterceptor implements HandlerInterceptor {
    private final Consumer<FactoryScope> admission;
    private final PlatformAuthorization platformAuthorization;
    private final FactorySelectionService selection;
    private final AuthorizationService authorization;

    public FoundationAccessInterceptor(FactorySelectionService selection, AuthorizationService authorization) {
        this(selection, authorization, (actor, permission) -> false);
    }

    public FoundationAccessInterceptor(FactorySelectionService selection, AuthorizationService authorization,
                                       PlatformAuthorization platformAuthorization) {
        this(selection, authorization, platformAuthorization, null);
    }

    public FoundationAccessInterceptor(FactorySelectionService selection, AuthorizationService authorization,
                                       PlatformAuthorization platformAuthorization, Consumer<FactoryScope> admission) {
        this.admission = admission;
        this.selection = selection;
        this.authorization = authorization;
        this.platformAuthorization = platformAuthorization;
    }

    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod method)) throw new FoundationException(ErrorCode.NOT_FOUND);
        String path = request.getRequestURI().substring(request.getContextPath().length());
        EndpointAccess access = EndpointCatalog.required(method, path);
        if (access.value() == EndpointAccess.Kind.PUBLIC) {
            return true;
        }
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated() || authentication instanceof AnonymousAuthenticationToken)
            throw new FoundationException(ErrorCode.UNAUTHENTICATED);
        if (authentication.getPrincipal() instanceof OpenApiPrincipal) {
            // The dedicated interceptor already checks current client/API grants.
            // External identity must never resolve an internal user's factory preference.
            if (!OpenApiHttp.isOpenApi(request)
                    || access.value() != EndpointAccess.Kind.AUTHENTICATED
                    || access.permissionScope() != EndpointAccess.PermissionScope.PLATFORM)
                throw new FoundationException(ErrorCode.FORBIDDEN);
            return true;
        }
        AuthenticatedActor actor = new AuthenticatedActor(authentication.getName());
        boolean platform = EndpointScopePolicy.scope(path) == EndpointAccess.PermissionScope.PLATFORM;
        if (platform) {
            FactoryContext.clear();
            if (EndpointScopePolicy.platformManagement(path) && !platformAuthorization.hasPlatformRole(actor)) {
                throw new FoundationException(ErrorCode.FORBIDDEN);
            }
        } else {
            String explicit = request.getHeader("X-Factory-Id");
            Optional<FactoryId> requested;
            try {
                requested = explicit == null ? Optional.empty() : Optional.of(new FactoryId(explicit));
            } catch (IllegalArgumentException failure) {
                throw new FoundationException(ErrorCode.BAD_REQUEST, failure);
            }
            var snapshot = BusinessRequestSnapshot.current(request);
            if (snapshot.isPresent() && requested.isPresent()
                    && !requested.get().equals(snapshot.get().binding().factoryId()))
                throw new FoundationException(ErrorCode.CONFLICT);
            var scope = snapshot.map(value -> new FactoryScope(value.binding().factoryId()))
                    .or(() -> selection.resolve(actor, requested));
            scope.ifPresent(FactoryContext::bind);
        }
        if (access.value() == EndpointAccess.Kind.AUTHORIZED) {
            boolean allowed = platform
                    ? platformAuthorization.hasPermission(actor, new Permission(access.permission()))
                    : authorization.hasPermission(actor, FactoryContext.required(), new Permission(access.permission()));
            if (!allowed) throw new FoundationException(ErrorCode.FORBIDDEN);
        }
        if (!platform && admission != null && !EndpointScopePolicy.factorySelection(path)) {
            // Identity-only requests may have no selected factory during initialization.
            // Business authorization and database access still require their own valid scope.
            FactoryContext.current().ifPresent(admission);
        }
        return true;
    }
}
