package dev.mesbase.infrastructure.persistence;

import java.util.Objects;
import java.util.function.Supplier;
import dev.mesbase.core.error.ErrorCode;
import dev.mesbase.core.error.FoundationException;

/** Internal selection scope; external connection identities are independent of FactoryScope. */
final class ErpSourceContext {
    private static final ThreadLocal<String> CURRENT = new ThreadLocal<>();
    private static final ThreadLocal<String> PINNED = new ThreadLocal<>();
    private ErpSourceContext() {}
    static void validate(String id) {
        if (id == null || !id.matches("[a-zA-Z0-9_-]{1,64}")) throw new FoundationException(ErrorCode.BAD_REQUEST);
    }
    static String required() {
        String id = CURRENT.get();
        if (id == null) throw new FoundationException(ErrorCode.INTEGRATION_NOT_CONFIGURED);
        return id;
    }
    static <T> T withSource(String id, Supplier<T> work) {
        String previous = select(id);
        try { return work.get(); }
        finally { restore(previous); }
    }
    static String select(String id) {
        validate(id);
        if (PINNED.get() != null && !PINNED.get().equals(id)) throw new FoundationException(ErrorCode.CONFLICT);
        String previous = CURRENT.get(); CURRENT.set(id);
        return previous;
    }
    static void restore(String previous) {
        if (previous == null) CURRENT.remove(); else CURRENT.set(previous);
    }
    static final class Pin implements AutoCloseable {
        private final String previous;
        private boolean closed;
        Pin(String id) {
            if (!Objects.equals(required(), id)) throw new FoundationException(ErrorCode.CONFLICT);
            previous = PINNED.get();
            if (previous != null && !previous.equals(id)) throw new FoundationException(ErrorCode.CONFLICT);
            PINNED.set(id);
        }
        public void close() {
            if (!closed) { closed = true; if (previous == null) PINNED.remove(); else PINNED.set(previous); }
        }
    }
}
