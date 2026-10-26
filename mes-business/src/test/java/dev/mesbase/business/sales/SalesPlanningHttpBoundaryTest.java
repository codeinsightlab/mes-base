package dev.mesbase.business.sales;

import dev.mesbase.business.production.controller.MesProductionPlanController;
import dev.mesbase.business.production.controller.MesProductionPlanItemController;
import dev.mesbase.business.sales.controller.MesCustomerController;
import dev.mesbase.business.sales.controller.MesSalesOrderController;
import dev.mesbase.business.sales.controller.MesSalesOrderItemController;
import dev.mesbase.platform.web.EndpointAccess;
import java.lang.reflect.Method;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class SalesPlanningHttpBoundaryTest {
    @Test
    void onlyAggregateControllersExposeOrderAndPlanWrites() {
        assertWriteMethods(MesSalesOrderController.class, List.of("create", "update", "delete"));
        assertWriteMethods(MesProductionPlanController.class, List.of("create", "update", "delete"));
        assertNoWriteMethods(MesSalesOrderItemController.class);
        assertNoWriteMethods(MesProductionPlanItemController.class);
    }

    @Test
    void customerWritesUseTheCustomerManagementControllerBoundary() {
        assertWriteMethods(MesCustomerController.class, List.of("create", "update", "delete"));
    }

    private static void assertWriteMethods(Class<?> controller, List<String> names) {
        List<Method> methods = List.of(controller.getDeclaredMethods()).stream()
                .filter(method -> hasWriteMapping(method))
                .toList();
        assertEquals(names.stream().sorted().toList(), methods.stream().map(Method::getName).sorted().toList());
        for (Method method : methods) assertNotNull(method.getAnnotation(EndpointAccess.class));
    }

    private static void assertNoWriteMethods(Class<?> controller) {
        assertEquals(0, List.of(controller.getDeclaredMethods()).stream().filter(SalesPlanningHttpBoundaryTest::hasWriteMapping).count());
        for (Method method : controller.getDeclaredMethods()) {
            if (method.getAnnotation(org.springframework.web.bind.annotation.GetMapping.class) != null) {
                assertNotNull(method.getAnnotation(EndpointAccess.class));
            }
        }
    }

    private static boolean hasWriteMapping(Method method) {
        return method.getAnnotation(PostMapping.class) != null
                || method.getAnnotation(PutMapping.class) != null
                || method.getAnnotation(DeleteMapping.class) != null;
    }
}
