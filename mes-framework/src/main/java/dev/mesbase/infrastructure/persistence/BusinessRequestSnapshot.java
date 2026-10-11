package dev.mesbase.infrastructure.persistence;

import dev.mesbase.core.error.ErrorCode;
import dev.mesbase.core.error.FoundationException;
import dev.mesbase.core.factory.FactoryScope;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Optional;
import java.util.function.Supplier;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Request-owned immutable binding and lazy transaction lease acquisition, independent of Core FactoryScope.
 */
public final class BusinessRequestSnapshot {
    private static final String KEY = BusinessRequestSnapshot.class.getName();
    private final BusinessRuntimeBinding binding;
    private final Supplier<RuntimeRegistry.Lease> lease;

    public BusinessRequestSnapshot(BusinessRuntimeBinding binding,
                                   Supplier<RuntimeRegistry.Lease> lease) {
        this.binding = binding;
        this.lease = lease;
    }

    public BusinessRuntimeBinding binding() {
        return binding;
    }

    public RuntimeRegistry.Lease acquire() {
        return lease.get();
    }

    public static Optional<BusinessRequestSnapshot> current(HttpServletRequest request) {
        return request.getAttribute(KEY) instanceof BusinessRequestSnapshot snapshot ? Optional.of(snapshot) : Optional.empty();
    }

    public static Optional<BusinessRequestSnapshot> current(FactoryScope scope) {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
            var value = attrs.getRequest().getAttribute(KEY);
            if (value instanceof BusinessRequestSnapshot snapshot) {
                if (!snapshot.binding.factoryId().equals(scope.factoryId()))
                    throw new FoundationException(ErrorCode.CONFLICT);
                return Optional.of(snapshot);
            }
        }
        return Optional.empty();
    }

    public static void install(BusinessRequestSnapshot snapshot) {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs)) {
            throw new IllegalStateException("HTTP request snapshot required");
        }
        var request = attrs.getRequest();
        if (request.getAttribute(KEY) != null) {
            throw new FoundationException(ErrorCode.CONFLICT);
        }
        request.setAttribute(KEY, snapshot);
    }

    public static void finish(HttpServletRequest request) {
        // Async redispatch may still need the immutable binding; no pool resource is held here.
        if (!request.isAsyncStarted()) request.removeAttribute(KEY);
    }
}
