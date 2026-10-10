package dev.mesbase.platform.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.method.HandlerMethod;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.authentication.AnonymousAuthenticationToken;

import java.util.Optional;

import dev.mesbase.core.factory.FactoryId;
import dev.mesbase.core.factory.FactoryScope;
import dev.mesbase.core.security.AuthenticatedActor;
import dev.mesbase.core.security.AuthorizationService;
import dev.mesbase.core.factory.FactorySelectionService;
import dev.mesbase.core.security.Permission;
import dev.mesbase.core.error.ErrorCode;
import dev.mesbase.core.error.FoundationException;
import dev.mesbase.platform.execution.FactoryContext;
import dev.mesbase.platform.security.PlatformAuthorization;

public final class FoundationAccessInterceptor implements HandlerInterceptor {
    private final PlatformAuthorization platformAuthorization;
    private final FactorySelectionService selection;
    private final AuthorizationService authorization;

    public FoundationAccessInterceptor(FactorySelectionService selection, AuthorizationService authorization) {
        this(selection, authorization, (actor, permission) -> false);
    }

    public FoundationAccessInterceptor(FactorySelectionService selection, AuthorizationService authorization,
                                       PlatformAuthorization platformAuthorization) {
        this.selection = selection;
        this.authorization = authorization;
        this.platformAuthorization = platformAuthorization;
    }

    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod method)) throw new FoundationException(ErrorCode.NOT_FOUND);
        EndpointAccess access = EndpointCatalog.required(method);
        if (access.value() == EndpointAccess.Kind.PUBLIC) return true;
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated() || authentication instanceof AnonymousAuthenticationToken)
            throw new FoundationException(ErrorCode.UNAUTHENTICATED);
        AuthenticatedActor actor = new AuthenticatedActor(authentication.getName());
        String explicit = request.getHeader("X-Factory-Id");
        Optional<FactoryId> requested;
        try {
            requested = explicit == null ? Optional.empty() : Optional.of(new FactoryId(explicit));
        } catch (IllegalArgumentException failure) {
            throw new FoundationException(ErrorCode.BAD_REQUEST, failure);
        }
        var scope = access.permissionScope() == EndpointAccess.PermissionScope.PLATFORM && explicit == null
                ? Optional.<FactoryScope>empty() : selection.resolve(actor, requested);
        scope.ifPresent(FactoryContext::bind);
        if (access.value() == EndpointAccess.Kind.AUTHORIZED) {
            boolean allowed = access.permissionScope() == EndpointAccess.PermissionScope.PLATFORM
                    ? platformAuthorization.hasPermission(actor, new Permission(access.permission()))
                    : authorization.hasPermission(actor, FactoryContext.required(), new Permission(access.permission()));
            if (!allowed) throw new FoundationException(ErrorCode.FORBIDDEN);
        }
        return true;
    }
}
