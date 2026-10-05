package dev.mesbase;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import dev.mesbase.platform.execution.*;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import java.util.concurrent.*;
class ExecutionScopeTest {
    @AfterEach void clean() { FactoryContext.clear();SecurityContextHolder.clearContext(); }
    @Test void platformHasNoFactoryAndRestoresCaller() {
        FactoryContext.bind(RuntimeFixture.A);ExecutionScope.platform().run(() -> assertTrue(FactoryContext.current().isEmpty()));assertEquals(RuntimeFixture.A,FactoryContext.required());
    }
    @Test void factoryRequiresIdentity() { assertThrows(NullPointerException.class,() -> ExecutionScope.factory(null));assertThrows(dev.mesbase.core.error.FoundationException.class,ExecutionScope::captureFactory); }
    @Test void factoryJobFailureRestoresAndCleans() {
        assertThrows(IllegalStateException.class,() -> ExecutionScope.factory(RuntimeFixture.B).run(() -> { assertEquals(RuntimeFixture.B,FactoryContext.required());throw new IllegalStateException("test only"); }));assertTrue(FactoryContext.current().isEmpty());
    }
    @Test void workerPropagatesExplicitScopeAndClearsNextJob() throws Exception {
        try(var executor=new ControlledExecutor(1,2)) {
            FactoryContext.bind(RuntimeFixture.A);SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated("test-only-actor","",java.util.List.of()));
            executor.submit(ExecutionScope.captureFactory(),() -> { assertEquals(RuntimeFixture.A,FactoryContext.required());assertNull(SecurityContextHolder.getContext().getAuthentication()); }).get(5,TimeUnit.SECONDS);
            executor.submit(ExecutionScope.platform(),() -> { assertTrue(FactoryContext.current().isEmpty());assertNull(SecurityContextHolder.getContext().getAuthentication()); }).get(5,TimeUnit.SECONDS);
            assertEquals("test-only-actor",SecurityContextHolder.getContext().getAuthentication().getName());
        }
    }
    @Test void callerRunsRestoresFactoryAndIdentityEvenOnFailure() throws Exception {
        CountDownLatch started=new CountDownLatch(1),release=new CountDownLatch(1);
        try(var executor=new ControlledExecutor(1,1)) {
            var blocked=executor.submit(ExecutionScope.platform(),() -> { started.countDown();try { if(!release.await(5,TimeUnit.SECONDS)) throw new AssertionError("test timeout"); } catch(InterruptedException e) { Thread.currentThread().interrupt();throw new IllegalStateException(e); } });
            assertTrue(started.await(5,TimeUnit.SECONDS)); var queued=executor.submit(ExecutionScope.platform(),() -> assertTrue(FactoryContext.current().isEmpty()));
            FactoryContext.bind(RuntimeFixture.A);var auth=UsernamePasswordAuthenticationToken.authenticated("test-only-caller","",java.util.List.of());SecurityContextHolder.getContext().setAuthentication(auth);
            Thread caller=Thread.currentThread();
            try {
                var inline=executor.submit(ExecutionScope.factory(RuntimeFixture.B),() -> { assertSame(caller,Thread.currentThread());assertEquals(RuntimeFixture.B,FactoryContext.required());assertNull(SecurityContextHolder.getContext().getAuthentication());throw new IllegalStateException("test only"); });
                assertThrows(ExecutionException.class,() -> inline.get(5,TimeUnit.SECONDS));assertEquals(RuntimeFixture.A,FactoryContext.required());assertSame(auth,SecurityContextHolder.getContext().getAuthentication());
            } finally { release.countDown(); }
            blocked.get(5,TimeUnit.SECONDS);queued.get(5,TimeUnit.SECONDS);
        }
    }
    @Test void closedExecutorExplicitlyRejects() { var executor=new ControlledExecutor(1,1);executor.close();assertThrows(RejectedExecutionException.class,() -> executor.submit(ExecutionScope.platform(),() -> {})); }
}
