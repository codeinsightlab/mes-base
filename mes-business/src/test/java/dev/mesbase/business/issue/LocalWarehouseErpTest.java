package dev.mesbase.business.issue;

import dev.mesbase.business.integration.warehouse.LocalWarehouseErpService;
import dev.mesbase.business.integration.warehouse.MaterialIssueCreateCommand;
import dev.mesbase.business.integration.warehouse.WarehouseIntegration;
import dev.mesbase.business.integration.warehouse.MaterialIssueCancelCommand;
import dev.mesbase.business.integration.warehouse.QueryMaterialIssueCommand;
import dev.mesbase.business.integration.warehouse.ErpActionResult.Mode;
import dev.mesbase.business.integration.warehouse.ErpActionResult.Status;
import dev.mesbase.business.integration.warehouse.ErpIssueQueryResult.Outcome;
import dev.mesbase.business.integration.warehouse.ErpIssueQueryResult.DocumentStatus;
import dev.mesbase.core.factory.FactoryScope;
import dev.mesbase.core.factory.FactoryId;
import dev.mesbase.core.error.FoundationException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LocalWarehouseErpTest {
    @Test
    void localActionsAndQueryNeverInventExternalFacts() {
        var scope = new FactoryScope(new FactoryId("A"));
        var service = new LocalWarehouseErpService();
        var create = service.createMaterialIssue(scope, new MaterialIssueCreateCommand(1L, "stable", "ISSUE-1", null, null));
        var cancel = service.cancelMaterialIssue(scope, new MaterialIssueCancelCommand(1L, "stable", "local", null));
        assertEquals(Status.SUCCEEDED, create.status());
        assertEquals(Mode.LOCAL_ONLY, create.executionMode());
        assertNull(create.externalReference());
        assertEquals(Status.SUCCEEDED, cancel.status());
        assertNull(cancel.externalReference());
        var query = service.queryMaterialIssue(scope, new QueryMaterialIssueCommand(1L, null, "local", null, null));
        assertEquals(Outcome.NOT_FOUND, query.outcome());
        assertEquals(DocumentStatus.UNKNOWN, query.documentStatus());
        assertNull(query.externalId());
    }

    @Test
    void configurationAndHistoricalIdentityFailClosed() {
        var scope = new FactoryScope(new FactoryId("A"));
        var env = new MockEnvironment();
        var integration = new WarehouseIntegration(env, List.of());
        var command = new QueryMaterialIssueCommand(1L, null, null, null, null);
        assertThrows(FoundationException.class, () -> integration.query(scope, "LOCAL_ONLY", "local", command));
        env.setProperty("mes.integration.warehouse.factories.A.mode", "CONNECTED");
        env.setProperty("mes.integration.warehouse.factories.A.provider", "digiwin");
        assertThrows(FoundationException.class, () -> integration.query(scope, "CONNECTED", "digiwin", new QueryMaterialIssueCommand(1L, null, "digiwin", "ERP-1", null)));
        env.setProperty("mes.integration.warehouse.factories.A.mode", "LOCAL_ONLY");
        assertThrows(FoundationException.class, () -> integration.query(scope, "LOCAL_ONLY", "local", new QueryMaterialIssueCommand(1L, null, null, "ERP-1", null)));
    }
}
