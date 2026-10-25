package dev.mesbase.business.sales;

import dev.mesbase.business.sales.dao.MesCustomerMapper;
import dev.mesbase.business.sales.dao.MesSalesOrderMapper;
import dev.mesbase.business.sales.domain.CustomerCreateRequest;
import dev.mesbase.business.sales.domain.CustomerUpdateRequest;
import dev.mesbase.business.sales.domain.MesCustomerEntity;
import dev.mesbase.business.sales.service.CustomerManagementService;
import dev.mesbase.common.core.domain.entity.SysUser;
import dev.mesbase.common.core.domain.model.LoginUser;
import dev.mesbase.core.error.FoundationException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CustomerManagementServiceTest {
    private final AtomicReference<MesCustomerEntity> record = new AtomicReference<>();
    private final AtomicInteger updates = new AtomicInteger();
    private final AtomicInteger deletes = new AtomicInteger();
    private boolean duplicateCode;
    private long references;
    private CustomerManagementService service;

    @BeforeEach
    void setUp() {
        MesCustomerMapper customers = proxy(MesCustomerMapper.class, (method, arguments) -> {
            if (method.getName().equals("insertSelective")) {
                if (duplicateCode) throw new DuplicateKeyException("TEST_ONLY duplicate customer code");
                MesCustomerEntity row = (MesCustomerEntity) arguments[0];
                row.setId(51L);
                record.set(row);
                return 1;
            }
            if (method.getName().equals("selectByPrimaryKeyForUpdate")
                    || method.getName().equals("selectByPrimaryKey")) return record.get();
            if (method.getName().equals("updateByPrimaryKeySelective")) {
                updates.incrementAndGet();
                return 1;
            }
            if (method.getName().equals("deleteByPrimaryKey")) {
                deletes.incrementAndGet();
                return 1;
            }
            return defaultValue(method);
        });
        MesSalesOrderMapper orders = proxy(MesSalesOrderMapper.class, (method, arguments) ->
                method.getName().equals("countByExample") ? references : defaultValue(method));
        service = new CustomerManagementService(customers, orders);
        authenticate();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void createSetsTrustedStatusSourceRevisionAndAuditIdentity() {
        MesCustomerEntity created = service.create(new CustomerCreateRequest(
                "CUSTOMER-TEST", "测试客户", null, null, null, null, null));

        assertEquals("ENABLED", created.getStatus());
        assertEquals("LOCAL", created.getSourceType());
        assertEquals(0, created.getRevision());
        assertEquals(99L, created.getCreatedBy());
        assertEquals(99L, created.getUpdatedBy());
        assertFalse(List.of(CustomerCreateRequest.class.getRecordComponents()).stream()
                .anyMatch(component -> Set.of("sourceType", "externalId", "revision", "createdBy", "updatedBy")
                        .contains(component.getName())));
    }

    @Test
    void duplicateCodeIsReportedAsConflictWithoutAcceptingASecondRecord() {
        duplicateCode = true;

        assertThrows(FoundationException.class, () -> service.create(new CustomerCreateRequest(
                "DUPLICATE", "重复编码", null, null, null, null, null)));

        assertEquals(0, updates.get());
        assertEquals(0, deletes.get());
    }

    @Test
    void staleRevisionAndExternalSourceCannotBeUpdated() {
        MesCustomerEntity row = customer(2, "LOCAL");
        record.set(row);

        assertThrows(FoundationException.class, () -> service.update(51L, update(1)));
        assertEquals(0, updates.get());

        row.setRevision(2);
        row.setSourceType("EXTERNAL");
        assertThrows(FoundationException.class, () -> service.update(51L, update(2)));
        assertEquals(0, updates.get());
    }

    @Test
    void referencedCustomerCannotBeDeleted() {
        record.set(customer(0, "LOCAL"));
        references = 1;

        assertThrows(FoundationException.class, () -> service.delete(List.of(51L)));

        assertEquals(0, deletes.get());
    }

    private static MesCustomerEntity customer(int revision, String source) {
        MesCustomerEntity row = new MesCustomerEntity();
        row.setId(51L);
        row.setCode("CUSTOMER-TEST");
        row.setName("测试客户");
        row.setStatus("ENABLED");
        row.setSourceType(source);
        row.setRevision(revision);
        return row;
    }

    private static CustomerUpdateRequest update(int revision) {
        return new CustomerUpdateRequest(revision, "CUSTOMER-TEST", "测试客户更新",
                null, null, null, null, "ENABLED", null);
    }

    private static void authenticate() {
        SysUser user = new SysUser();
        user.setUserId(99L);
        user.setUserName("S1_TEST_ONLY");
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new LoginUser(99L, 1L, user, Set.of()), null, List.of()));
    }

    private static <T> T proxy(Class<T> type, TestInvocationHandler handler) {
        Object value = Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type}, (proxy, method, arguments) -> {
            if (method.getDeclaringClass() == Object.class) {
                if (method.getName().equals("toString")) return type.getSimpleName() + "TestProxy";
                if (method.getName().equals("hashCode")) return System.identityHashCode(proxy);
                if (method.getName().equals("equals")) return proxy == arguments[0];
            }
            return handler.invoke(method, arguments == null ? new Object[0] : arguments);
        });
        return type.cast(value);
    }

    private static Object defaultValue(Method method) {
        Class<?> type = method.getReturnType();
        if (!type.isPrimitive()) return null;
        if (type == boolean.class) return false;
        if (type == char.class) return '\0';
        if (type == byte.class) return (byte) 0;
        if (type == short.class) return (short) 0;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == float.class) return 0F;
        if (type == double.class) return 0D;
        return null;
    }

    @FunctionalInterface
    private interface TestInvocationHandler {
        Object invoke(Method method, Object[] arguments) throws Throwable;
    }
}
