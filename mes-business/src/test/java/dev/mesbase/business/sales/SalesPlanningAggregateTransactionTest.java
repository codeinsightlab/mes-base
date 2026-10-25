package dev.mesbase.business.sales;

import dev.mesbase.business.basic.dao.MaterialMapper;
import dev.mesbase.business.basic.domain.MaterialEntity;
import dev.mesbase.business.production.dao.MesProductionPlanItemMapper;
import dev.mesbase.business.production.dao.MesProductionPlanMapper;
import dev.mesbase.business.production.domain.MesProductionPlanEntity;
import dev.mesbase.business.production.domain.MesProductionPlanItemCreateCommand;
import dev.mesbase.business.production.domain.ProductionPlanCreateRequest;
import dev.mesbase.business.production.domain.ProductionPlanLineRequest;
import dev.mesbase.business.production.service.IMesProductionPlanItemService;
import dev.mesbase.business.production.service.ProductionPlanDocumentService;
import dev.mesbase.business.sales.dao.MesCustomerMapper;
import dev.mesbase.business.sales.dao.MesSalesOrderItemMapper;
import dev.mesbase.business.sales.dao.MesSalesOrderMapper;
import dev.mesbase.business.sales.domain.MesCustomerEntity;
import dev.mesbase.business.sales.domain.MesSalesOrderEntity;
import dev.mesbase.business.sales.domain.MesSalesOrderItemCreateCommand;
import dev.mesbase.business.sales.domain.SalesOrderCreateRequest;
import dev.mesbase.business.sales.domain.SalesOrderLineRequest;
import dev.mesbase.business.sales.service.IMesSalesOrderItemService;
import dev.mesbase.business.sales.service.SalesOrderDocumentService;
import dev.mesbase.common.core.domain.entity.SysUser;
import dev.mesbase.common.core.domain.model.LoginUser;
import dev.mesbase.core.error.FoundationException;
import dev.mesbase.infrastructure.persistence.OwnedTransactionManager;
import java.math.BigDecimal;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
class SalesPlanningAggregateTransactionTest {
    private AnnotationConfigApplicationContext context;
    private JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        context = new AnnotationConfigApplicationContext(TestConfig.class);
        jdbc = context.getBean(JdbcTemplate.class);
        authenticate();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        context.close();
    }

    @Test
    void salesOrderCreateRollsBackHeaderAndPriorLinesWhenLaterLineWriteFails() {
        SalesOrderDocumentService service = context.getBean(SalesOrderDocumentService.class);
        SalesOrderCreateRequest request = new SalesOrderCreateRequest(3L, LocalDate.of(2026, 10, 25), null,
                List.of(line("1.25"), line("2.5")));

        assertThrows(IllegalStateException.class, () -> service.create(request));

        assertEquals(0, jdbc.queryForObject("select count(*) from sales_order_header", Integer.class));
        assertEquals(0, jdbc.queryForObject("select count(*) from sales_order_line", Integer.class));
    }

    @Test
    void productionPlanCreateRollsBackHeaderAndPriorLinesWhenLaterLineWriteFails() {
        ProductionPlanDocumentService service = context.getBean(ProductionPlanDocumentService.class);
        ProductionPlanCreateRequest request = new ProductionPlanCreateRequest(LocalDate.of(2026, 10, 25), null,
                List.of(planLine("1.25"), planLine("2.5")));

        assertThrows(IllegalStateException.class, () -> service.create(request));

        assertEquals(0, jdbc.queryForObject("select count(*) from production_plan_header", Integer.class));
        assertEquals(0, jdbc.queryForObject("select count(*) from production_plan_line", Integer.class));
    }

    @Test
    void productionPlanRejectsInvalidDateBeforeAnyWrite() {
        ProductionPlanDocumentService service = context.getBean(ProductionPlanDocumentService.class);
        ProductionPlanLineRequest invalid = new ProductionPlanLineRequest(null, 10L, BigDecimal.ONE,
                LocalDateTime.of(2026, 10, 26, 0, 0), LocalDateTime.of(2026, 10, 25, 0, 0), 0, null);
        ProductionPlanCreateRequest request = new ProductionPlanCreateRequest(LocalDate.of(2026, 10, 25), null,
                List.of(invalid));

        assertThrows(FoundationException.class, () -> service.create(request));

        assertEquals(0, jdbc.queryForObject("select count(*) from production_plan_header", Integer.class));
        assertEquals(0, jdbc.queryForObject("select count(*) from production_plan_line", Integer.class));
    }

    private static SalesOrderLineRequest line(String quantity) {
        return new SalesOrderLineRequest(null, 10L, new BigDecimal(quantity), null, null);
    }

    private static ProductionPlanLineRequest planLine(String quantity) {
        return new ProductionPlanLineRequest(null, 10L, new BigDecimal(quantity), null, null, 0, null);
    }

    private static void authenticate() {
        SysUser user = new SysUser();
        user.setUserId(99L);
        user.setUserName("S1_TEST_ONLY");
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new LoginUser(99L, 1L, user, Set.of()), null, List.of()));
    }

    @Configuration
    @EnableTransactionManagement(proxyTargetClass = true)
    static class TestConfig {
        private static final long ORDER_ID = 101L;
        private static final long PLAN_ID = 201L;

        @Bean
        DriverManagerDataSource dataSource() {
            DriverManagerDataSource dataSource = new DriverManagerDataSource();
            dataSource.setDriverClassName("org.h2.Driver");
            dataSource.setUrl("jdbc:h2:mem:s1_" + UUID.randomUUID().toString().replace("-", "")
                    + ";DB_CLOSE_DELAY=-1;MODE=MySQL");
            dataSource.setUsername("sa");
            dataSource.setPassword("");
            return dataSource;
        }

        @Bean
        OwnedTransactionManager businessTransactionManager(DriverManagerDataSource dataSource) {
            return new OwnedTransactionManager(dataSource);
        }

        @Bean
        JdbcTemplate jdbcTemplate(DriverManagerDataSource dataSource) {
            JdbcTemplate jdbc = new JdbcTemplate(dataSource);
            jdbc.execute("create table sales_order_header (id bigint primary key, order_no varchar(64) not null)");
            jdbc.execute("create table sales_order_line (id bigint generated by default as identity primary key, "
                    + "sales_order_id bigint not null, line_no integer not null, ordered_qty decimal(20,6) not null)");
            jdbc.execute("create table production_plan_header (id bigint primary key, plan_no varchar(64) not null)");
            jdbc.execute("create table production_plan_line (id bigint generated by default as identity primary key, "
                    + "plan_id bigint not null, line_no integer not null, planned_qty decimal(20,6) not null)");
            return jdbc;
        }

        @Bean
        MesCustomerMapper customerMapper() {
            MesCustomerEntity customer = new MesCustomerEntity();
            customer.setId(3L);
            customer.setStatus("ENABLED");
            customer.setSourceType("LOCAL");
            return proxy(MesCustomerMapper.class, (method, arguments) ->
                    method.getName().equals("selectByPrimaryKeyForUpdate") ? customer : defaultValue(method));
        }

        @Bean
        MaterialMapper materialMapper() {
            MaterialEntity material = new MaterialEntity();
            material.setId(10L);
            material.setStatus("ENABLED");
            material.setUnitId(20L);
            return proxy(MaterialMapper.class, (method, arguments) ->
                    method.getName().equals("selectByPrimaryKeyForUpdate") ? material : defaultValue(method));
        }

        @Bean
        MesSalesOrderMapper salesOrderMapper(JdbcTemplate jdbc) {
            AtomicReference<MesSalesOrderEntity> saved = new AtomicReference<>();
            return proxy(MesSalesOrderMapper.class, (method, arguments) -> {
                if (method.getName().equals("insertSelective")) {
                    MesSalesOrderEntity header = (MesSalesOrderEntity) arguments[0];
                    header.setId(ORDER_ID);
                    jdbc.update("insert into sales_order_header(id, order_no) values (?, ?)",
                            header.getId(), header.getOrderNo());
                    saved.set(header);
                    return 1;
                }
                if (method.getName().equals("selectByPrimaryKey")) return saved.get();
                return defaultValue(method);
            });
        }

        @Bean
        MesSalesOrderItemMapper salesOrderItemMapper() {
            return proxy(MesSalesOrderItemMapper.class, (method, arguments) -> defaultValue(method));
        }

        @Bean
        IMesSalesOrderItemService salesOrderItemService(JdbcTemplate jdbc) {
            AtomicInteger writes = new AtomicInteger();
            return proxy(IMesSalesOrderItemService.class, (method, arguments) -> {
                if (method.getName().equals("insert")) {
                    MesSalesOrderItemCreateCommand line = (MesSalesOrderItemCreateCommand) arguments[0];
                    jdbc.update("insert into sales_order_line(sales_order_id, line_no, ordered_qty) values (?, ?, ?)",
                            line.salesOrderId(), line.lineNo(), line.orderedQty());
                    if (writes.incrementAndGet() == 2) {
                        throw new IllegalStateException("TEST_ONLY injected sales line failure");
                    }
                }
                return defaultValue(method);
            });
        }

        @Bean
        SalesOrderDocumentService salesOrderDocumentService(MesSalesOrderMapper orders,
                MesSalesOrderItemMapper items, MesCustomerMapper customers, MaterialMapper materials,
                IMesSalesOrderItemService itemService) {
            return new SalesOrderDocumentService(orders, items, customers, materials, itemService);
        }

        @Bean
        MesProductionPlanMapper productionPlanMapper(JdbcTemplate jdbc) {
            AtomicReference<MesProductionPlanEntity> saved = new AtomicReference<>();
            return proxy(MesProductionPlanMapper.class, (method, arguments) -> {
                if (method.getName().equals("insertSelective")) {
                    MesProductionPlanEntity header = (MesProductionPlanEntity) arguments[0];
                    header.setId(PLAN_ID);
                    jdbc.update("insert into production_plan_header(id, plan_no) values (?, ?)",
                            header.getId(), header.getPlanNo());
                    saved.set(header);
                    return 1;
                }
                if (method.getName().equals("selectByPrimaryKey")) return saved.get();
                return defaultValue(method);
            });
        }

        @Bean
        MesProductionPlanItemMapper productionPlanItemMapper() {
            return proxy(MesProductionPlanItemMapper.class, (method, arguments) -> defaultValue(method));
        }

        @Bean
        IMesProductionPlanItemService productionPlanItemService(JdbcTemplate jdbc) {
            AtomicInteger writes = new AtomicInteger();
            return proxy(IMesProductionPlanItemService.class, (method, arguments) -> {
                if (method.getName().equals("insert")) {
                    MesProductionPlanItemCreateCommand line = (MesProductionPlanItemCreateCommand) arguments[0];
                    jdbc.update("insert into production_plan_line(plan_id, line_no, planned_qty) values (?, ?, ?)",
                            line.planId(), line.lineNo(), line.plannedQty());
                    if (writes.incrementAndGet() == 2) {
                        throw new IllegalStateException("TEST_ONLY injected plan line failure");
                    }
                }
                return defaultValue(method);
            });
        }

        @Bean
        ProductionPlanDocumentService productionPlanDocumentService(MesProductionPlanMapper plans,
                MesProductionPlanItemMapper items, MaterialMapper materials, IMesProductionPlanItemService itemService) {
            return new ProductionPlanDocumentService(plans, items, materials, itemService);
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
}
