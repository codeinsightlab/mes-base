package dev.mesbase;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import dev.mesbase.platform.execution.*;
import dev.mesbase.core.error.*;
class FactoryScopeTest {
    @AfterEach void clean() { FactoryContext.clear(); }
    @Test void bindCurrentRequiredClear() {
        assertTrue(FactoryContext.current().isEmpty()); assertEquals(ErrorCode.FACTORY_SCOPE_REQUIRED,assertThrows(FoundationException.class,FactoryContext::required).code());
        FactoryContext.bind(RuntimeFixture.A);assertEquals(RuntimeFixture.A,FactoryContext.required());FactoryContext.clear();assertTrue(FactoryContext.current().isEmpty());
    }
    @Test void nestedRestoresParent() {
        FactoryContext.bind(RuntimeFixture.A);
        FactoryContext.runWithScope(RuntimeFixture.B,() -> { assertEquals(RuntimeFixture.B,FactoryContext.required());FactoryContext.runWithScope(RuntimeFixture.A,() -> assertEquals(RuntimeFixture.A,FactoryContext.required()));assertEquals(RuntimeFixture.B,FactoryContext.required()); });
        assertEquals(RuntimeFixture.A,FactoryContext.required());
    }
    @Test void exceptionRestoresParentAndEmpty() {
        assertThrows(IllegalStateException.class,() -> FactoryContext.runWithScope(RuntimeFixture.A,() -> { throw new IllegalStateException("test only"); }));assertTrue(FactoryContext.current().isEmpty());
        FactoryContext.bind(RuntimeFixture.A);assertThrows(IllegalStateException.class,() -> FactoryContext.runWithScope(RuntimeFixture.B,() -> { throw new IllegalStateException("test only"); }));assertEquals(RuntimeFixture.A,FactoryContext.required());
    }
    @Test void pinnedTransactionRejectsSwitchAndClear() {
        FactoryContext.runWithScope(RuntimeFixture.A,() -> FactoryContext.pinned(RuntimeFixture.A,() -> {
            assertThrows(FoundationException.class,() -> FactoryContext.bind(RuntimeFixture.B)); assertThrows(FoundationException.class,FactoryContext::clear); return null;
        })); assertTrue(FactoryContext.current().isEmpty());
    }
    @Test void domainScopeHasOnlyFactoryIdentity() { assertEquals(1,dev.mesbase.core.factory.FactoryScope.class.getRecordComponents().length); }
}
