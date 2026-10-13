package dev.mesbase.business.production;

import dev.mesbase.business.production.controller.RoutingController;
import dev.mesbase.business.production.controller.RoutingPermissions;
import dev.mesbase.business.production.domain.RoutingCopyCommand;
import dev.mesbase.business.production.domain.RoutingCreateCommand;
import dev.mesbase.business.production.domain.RoutingRevisionCommand;
import dev.mesbase.business.production.sync.UnconfiguredRoutingQueryAdapter;
import dev.mesbase.business.production.sync.UnconfiguredOperationQueryAdapter;
import dev.mesbase.core.error.ErrorCode;
import dev.mesbase.core.error.FoundationException;
import dev.mesbase.core.factory.FactoryId;
import dev.mesbase.core.factory.FactoryScope;
import dev.mesbase.platform.web.EndpointAccess;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * HTTP contract boundaries; database/lifecycle behavior is exercised by the real MySQL acceptance script.
 */
class RoutingLifecycleContractTest {
    @Test
    void callersCannotSupplyPublicationOrVersionInOrdinaryCreate() {
        var names = Arrays.stream(RoutingCreateCommand.class.getRecordComponents())
                .map(component -> component.getName()).toList();
        assertFalse(names.contains("factoryId"));
        assertFalse(names.contains("status"));
        assertFalse(names.contains("version"));
        assertFalse(names.contains("publishedAt"));
    }

    @Test
    void publishAndCopyRequireTheirOwnAuthorization() throws Exception {
        var publish = RoutingController.class.getMethod("publish", Long.class, RoutingRevisionCommand.class)
                .getAnnotation(EndpointAccess.class);
        var copy = RoutingController.class.getMethod("copy", Long.class, RoutingCopyCommand.class)
                .getAnnotation(EndpointAccess.class);
        assertEquals(RoutingPermissions.PUBLISH, publish.permission());
        assertEquals(RoutingPermissions.COPY, copy.permission());
        assertNotEquals(RoutingPermissions.UPDATE, publish.permission());
        assertNotEquals(RoutingPermissions.UPDATE, copy.permission());
    }

    @Test
    void unconfiguredSourcesNeverReturnAnEmptySuccess() {
        var scope = new FactoryScope(new FactoryId("TEST_ONLY"));
        assertEquals(ErrorCode.ERP_ROUTING_NOT_CONFIGURED,
                assertThrows(FoundationException.class, () -> new UnconfiguredRoutingQueryAdapter().query(scope, 100)).code());
        assertEquals(ErrorCode.ERP_OPERATION_NOT_CONFIGURED,
                assertThrows(FoundationException.class, () -> new UnconfiguredOperationQueryAdapter().query(scope, 100)).code());
    }
}
