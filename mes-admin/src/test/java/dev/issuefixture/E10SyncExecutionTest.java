package dev.issuefixture;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lixin.capability.digiwin.e10.response.E10QueryBusinessResponse;
import com.lixin.capability.digiwin.e10.response.E10OperationBusinessResponse;
import com.lixin.capability.digiwin.e10.contract.mo.MoContract;
import com.lixin.capability.digiwin.e10.contract.cms.WarehouseContract;
import com.lixin.capability.digiwin.e10.response.mo.MoQueryRow;
import com.lixin.capability.digiwin.e10.response.cms.WarehouseQueryRow;
import dev.mesbase.business.basic.dao.MaterialMapper;
import dev.mesbase.business.basic.dao.UnitMeasureMapper;
import dev.mesbase.business.production.dao.RoutingMapper;
import dev.mesbase.business.production.dao.RoutingProductMapper;
import dev.mesbase.business.production.dao.WorkorderMapper;
import dev.mesbase.business.production.service.WorkOrderService;
import dev.mesbase.business.wm.dao.WmWarehouseMapper;
import dev.mesbase.business.wm.service.IWmWarehouseService;
import dev.mesbase.business.wm.service.impl.WmWarehouseServiceImpl;
import dev.mesbase.core.error.ErrorCode;
import dev.mesbase.core.error.FoundationException;
import dev.mesbase.core.factory.FactoryId;
import dev.mesbase.core.factory.FactoryScope;
import dev.mesbase.framework.sync.SyncCoordinator;
import dev.mesbase.platform.execution.FactoryIntegrationContext;
import dev.mesbase.platform.execution.FactoryContext;
import dev.mesbase.acceptance.FactorySnapshotFixture;
import dev.mesbase.server.integration.e10.DigiwinE10MoQueries;
import dev.mesbase.server.integration.e10.DigiwinE10SyncMapping;
import dev.mesbase.server.integration.e10.DigiwinE10SyncWriter;
import dev.mesbase.server.integration.e10.DigiwinE10SyncService;
import dev.mesbase.server.integration.e10.DigiwinE10SyncPreview.Kind;
import dev.mesbase.server.integration.e10.E10SyncContract;
import dev.mesbase.server.integration.e10.FactoryOapiService;
import dev.mesbase.server.integration.e10.FactoryOapiSettings;
import java.util.List;
import java.math.BigDecimal;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.mesbase.common.core.domain.model.LoginUser;
import dev.mesbase.common.core.domain.entity.SysUser;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import java.util.Set;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * TEST ONLY: isolated A/B H2 databases, actual mapping, business services, SQL and Spring transactions. ERP is synthetic.
 */
class E10SyncExecutionTest {
    static final ObjectMapper JSON = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    static JsonNode raw(String code) {
        return JSON.createObjectNode().put("doc_no", code).put("sys_MainId", "ID-" + code).put("om_site_id", "SITE_A")
                .put("item_no", "M1").put("unit_no", "KG").put("plan_qty", new BigDecimal("100.123456"))
                .put("status", "TEST_DRAFT")
                .put("approve_status", "TEST_APPROVED").put("plan_start_date", "2026-10-01")
                .put("plan_complete_date", "2026-10-02");
    }

    static E10SyncContract.Rule rule(boolean mo) {
        return new E10SyncContract.Rule("TEST ONLY synthetic contract, never production evidence", List.of("/sys_MainId"),
                "/om_site_id", "SITE_A", null, null, "/status", mo ? Map.of("TEST_DRAFT", "DRAFT", "TEST_RELEASED", "RELEASED")
                : Map.of("TEST_DRAFT", "ENABLED"), "/approve_status", List.of("TEST_APPROVED"), "CODE", false,
                "/warehouse_no", "/owner", "/plant", "[]", JSON.createArrayNode()
                .add(JSON.createObjectNode().put("field_name", "sys_MainId").put("order_type", "asc")).toString());
    }

    static class Fixture implements AutoCloseable {
        final AnnotationConfigWebApplicationContext context = ProductionInputHttpTest.context();
        final JdbcTemplate db = ProductionInputHttpTest.db(context);
        final DigiwinE10MoQueries queries = mock(DigiwinE10MoQueries.class);
        final FactoryOapiService configs = mock(FactoryOapiService.class);
        final SyncCoordinator sync = context.getBean(SyncCoordinator.class);
        final DigiwinE10SyncMapping mapping;
        final DigiwinE10SyncWriter writer;
        final DigiwinE10SyncService service;
        final FactoryScope scope = new FactoryScope(new FactoryId("A"));

        Fixture() throws Exception {
            var beans = context.getAutowireCapableBeanFactory();
            context.getBeanFactory()
                    .registerSingleton("testE10Warehouses", beans.createBean(WmWarehouseServiceImpl.class));
            mapping = beans.createBean(DigiwinE10SyncMapping.class);
            context.getBeanFactory().registerSingleton("testE10Mapping", mapping);
            writer = beans.createBean(DigiwinE10SyncWriter.class);
            service = new DigiwinE10SyncService(queries, configs, mapping, writer, sync);
            when(sync.claim(eq(scope), anyString(), any())).thenAnswer(inv -> new SyncCoordinator.Run(scope, inv.getArgument(1), "TEST_RUN"));
            var settings = mock(FactoryOapiSettings.class);
            when(settings.syncContract()).thenReturn(new E10SyncContract(true, rule(true), rule(false)));
            when(configs.connection("A")).thenReturn(new FactoryOapiService.Connection("A", "TEST_BINDING", 1, settings));
        }

        void admitted(Runnable action) {
            var user = new SysUser();
            user.setUserId(1L);
            user.setUserName("TEST_ONLY");
            SecurityContextHolder.getContext()
                    .setAuthentication(new UsernamePasswordAuthenticationToken(new LoginUser(1L, 1L, user, Set.of()), "TEST_ONLY", List.of()));
            try {
                FactoryContext.runWithScope(scope, () -> FactoryIntegrationContext.run(
                        FactorySnapshotFixture.erp("A", "DIGIWIN_E10", "TEST_BINDING"), action));
            } finally {
                SecurityContextHolder.clearContext();
            }
        }

        @SuppressWarnings("unchecked")
        void orders(JsonNode... raws) {
            E10QueryBusinessResponse<MoQueryRow> result = mock(E10QueryBusinessResponse.class);
            var rows = List.of(raws).stream().map(x -> {
                ObjectNode typed = x.deepCopy();
                if (typed.get("plan_qty").isTextual())
                    typed.put("plan_qty", new BigDecimal(typed.get("plan_qty").asText()));
                return JSON.convertValue(typed, MoQueryRow.class);
            }).toList();
            when(result.getRows()).thenReturn(rows);
            when(queries.queryMo(any())).thenReturn(result);
        }

        @SuppressWarnings("unchecked")
        void warehouses(JsonNode... raws) {
            E10QueryBusinessResponse<WarehouseQueryRow> result = mock(E10QueryBusinessResponse.class);
            when(result.getRows()).thenReturn(List.of(raws).stream()
                    .map(x -> JSON.convertValue(x, WarehouseQueryRow.class)).toList());
            when(queries.queryWarehouse(any())).thenReturn(result);
        }

        public void close() {
            context.close();
        }
    }

    @Test
    void dryRunAndActualCreateUpdateReplayThroughRealServices() throws Exception {
        try (var f = new Fixture()) {
            var row = raw("TEST_E10_NEW");
            f.orders(row);
            f.admitted(() -> {
                var preview = f.service.execute(Kind.WORKORDER, false, 20, 5);
                assertEquals("DRY_RUN", preview.status());
                assertEquals(1, preview.creatable());
                assertEquals(0, preview.created());
                assertEquals(1, f.db.queryForObject("SELECT COUNT(*) FROM workorder", Integer.class));
                var created = f.service.execute(Kind.WORKORDER, true, 20, 5);
                assertEquals("COMMITTED", created.status());
                assertEquals(1, created.created());
                assertEquals("EXTERNAL", f.db.queryForObject("SELECT source FROM workorder WHERE code='TEST_E10_NEW'", String.class));
                assertEquals(1, f.service.execute(Kind.WORKORDER, true, 20, 5).unchanged());
                f.orders(row.deepCopy().withObject("").put("plan_qty", "200"));
                var updated = f.service.execute(Kind.WORKORDER, true, 20, 5);
                assertEquals(1, updated.updated());
                assertEquals(0, updated.created());
                assertEquals("LOCAL", f.db.queryForObject("SELECT source FROM workorder WHERE id=1", String.class));
                assertEquals(0, f.db.queryForObject("SELECT COUNT(*) FROM wm_stock_action", Integer.class));
            });
        }
    }

    @Test
    void warehouseCreateUpdateReplayAndLocalCollision() throws Exception {
        try (var f = new Fixture()) {
            var row = raw("UNUSED").deepCopy().withObject("").put("warehouse_no", "TEST_E10_WH")
                    .put("warehouse_name", "TEST ONLY ERP warehouse");
            f.warehouses(row);
            f.admitted(() -> {
                assertEquals(1, f.service.execute(Kind.WAREHOUSE, false, 20, 5).creatable());
                assertEquals(1, f.service.execute(Kind.WAREHOUSE, true, 20, 5).created());
                assertEquals(1, f.service.execute(Kind.WAREHOUSE, true, 20, 5).unchanged());
                f.warehouses(row.deepCopy().put("warehouse_name", "TEST ONLY changed"));
                assertEquals(1, f.service.execute(Kind.WAREHOUSE, true, 20, 5).updated());
                f.warehouses(row.deepCopy().put("warehouse_no", "W1"));
                assertEquals(1, f.service.execute(Kind.WAREHOUSE, true, 20, 5).conflicts());
                assertEquals("LOCAL", f.db.queryForObject("SELECT source FROM wm_warehouse WHERE id=1", String.class));
            });
        }
    }

    @Test
    void mappingMatrixNoGuessedUnitsRoutesStatusDatesOrIdentity() throws Exception {
        try (var f = new Fixture()) {
            f.admitted(() -> {
                assertEquals("IDENTITY_UNVERIFIED", f.mapping.prepare(Kind.WORKORDER, raw("X"), null).item().action());
                assertEquals("STATUS_UNVERIFIED", f.mapping.prepare(Kind.WORKORDER, raw("X").deepCopy().withObject("")
                        .put("status", "UNKNOWN"), rule(true)).item().action());
                assertEquals("CONFLICT", f.mapping.prepare(Kind.WORKORDER, raw("X").deepCopy().withObject("")
                        .put("om_site_id", "OTHER"), rule(true)).item().action());
                for (var pair : List.of(Map.entry("item_no", "MISSING"), Map.entry("unit_no", "EA"), Map.entry("plan_qty", "0.1234567"), Map.entry("plan_start_date", "bad-date")))
                    assertEquals("MAPPING_REQUIRED", f.mapping.prepare(Kind.WORKORDER, raw("X").deepCopy()
                            .withObject("").put(pair.getKey(), pair.getValue()), rule(true)).item().action());
                assertEquals("STATUS_UNVERIFIED", f.mapping.prepare(Kind.WORKORDER, raw("X").deepCopy().withObject("")
                        .put("approve_status", "N"), rule(true)).item().action());
                f.db.update("UPDATE routing SET status='PUBLISHED' WHERE id=2");
                f.db.update("INSERT INTO routing_product(routing_id,material_id) VALUES(2,1)");
                assertEquals("MAPPING_REQUIRED", f.mapping.prepare(Kind.WORKORDER, raw("X"), rule(true)).item()
                        .action());
            });
        }
    }

    @Test
    void duplicatesIncompletePaginationAndConflictingIdentityNeverCreateTwice() throws Exception {
        try (var f = new Fixture()) {
            f.orders(raw("X"), raw("X"));
            f.admitted(() -> {
                var result = f.service.execute(Kind.WORKORDER, true, 20, 5);
                assertEquals(1, result.created());
                assertEquals(1, result.duplicates());
                f.orders(raw("Y"), raw("Y").deepCopy().withObject("").put("plan_qty", "222"));
                assertEquals(2, f.service.execute(Kind.WORKORDER, true, 20, 5).conflicts());
                f.orders(raw("Z"));
                var bounded = f.service.execute(Kind.WORKORDER, true, 1, 1);
                assertEquals("FAILED", bounded.status());
                assertEquals(1, bounded.blocked());
                assertEquals(0, bounded.created());
            });
        }
    }

    @Test
    void partialSuccessCancelledFrozenAndConfigurationChange() throws Exception {
        try (var f = new Fixture()) {
            f.orders(raw("WO1"), raw("SAFE"));
            f.admitted(() -> {
                var result = f.service.execute(Kind.WORKORDER, true, 20, 5);
                assertEquals("PARTIAL_SUCCESS", result.status());
                assertEquals(1, result.created());
                assertEquals(1, result.conflicts());
                f.db.update("UPDATE workorder SET status='CANCELLED' WHERE code='SAFE'");
                f.orders(raw("SAFE"));
                assertEquals(1, f.service.execute(Kind.WORKORDER, true, 20, 5).conflicts());
                when(f.configs.connection("A")).thenReturn(new FactoryOapiService.Connection("A", "CHANGED", 2, mock(FactoryOapiSettings.class)));
                assertEquals(ErrorCode.INTEGRATION_NOT_CONFIGURED, assertThrows(FoundationException.class,
                        () -> f.service.execute(Kind.WORKORDER, true, 20, 5)).code());
            });
        }
    }

    @Test
    void lostTokenAtCommitRollsBackBothBusinessAndExternalMapping() throws Exception {
        try (var f = new Fixture()) {
            f.admitted(() -> {
                var calls = new AtomicInteger();
                doAnswer(inv -> {
                    if (calls.incrementAndGet() == 3) throw new FoundationException(ErrorCode.SYNC_EXECUTION_LOST);
                    return null;
                }).when(f.sync).assertOwner(any());
                var run = new SyncCoordinator.Run(f.scope, "workorder.erp", "TEST_RUN");
                assertThrows(FoundationException.class, () -> f.writer.write(Kind.WORKORDER, raw("ROLLBACK"), rule(true), run));
                assertEquals(0, f.db.queryForObject("SELECT COUNT(*) FROM workorder WHERE code='ROLLBACK'", Integer.class));
                assertEquals(0, f.db.queryForObject("SELECT COUNT(*) FROM workorder_erp_extra WHERE external_id='ID-ROLLBACK'", Integer.class));
            });
        }
    }

    static E10SyncContract.Rule withRead(boolean mo) {
        var r = rule(mo);
        return new E10SyncContract.Rule(r.evidence(), r.identityPointers(), r.sitePointer(), r.expectedSite(),
                r.organizationPointer(), r.expectedOrganization(), r.statusPointer(), r.statuses(), r.approvalPointer(),
                r.approvedValues(), r.productKey(), true, r.readCodePointer(), r.readOwnerPointer(), r.readPlantPointer(), r.conditionsJson(), r.ordersJson());
    }

    static void settings(Fixture f, E10SyncContract contract) {
        var settings = mock(FactoryOapiSettings.class);
        when(settings.syncContract()).thenReturn(contract);
        when(f.configs.connection("A")).thenReturn(new FactoryOapiService.Connection("A", "TEST_BINDING", 1, settings));
    }

    @Test
    @SuppressWarnings("unchecked")
    void readEnrichmentUsesVerifiedLocatorsAndRejectsMismatchedIdentity() throws Exception {
        try (var f = new Fixture()) {
            settings(f, new E10SyncContract(true, withRead(true), withRead(false)));
            var row = raw("READ_MO");
            f.orders(row);
            E10OperationBusinessResponse<MoContract.ReadResponseData> mo = mock(E10OperationBusinessResponse.class);
            when(mo.getRecords()).thenReturn(List.of(JSON.convertValue(row, MoContract.ReadResponseData.class)));
            when(f.queries.readMo("READ_MO")).thenReturn(mo);
            f.admitted(() -> assertEquals(1, f.service.execute(Kind.WORKORDER, true, 20, 5).created()));
            when(mo.getRecords()).thenReturn(List.of(JSON.convertValue(raw("WRONG"), MoContract.ReadResponseData.class)));
            f.admitted(() -> assertEquals(1, f.service.execute(Kind.WORKORDER, true, 20, 5).blocked()));
            var warehouse = raw("X").deepCopy().withObject("").put("warehouse_no", "READ_WH")
                    .put("warehouse_name", "TEST ONLY");
            f.warehouses(warehouse);
            f.admitted(() -> {
                var missing = f.service.execute(Kind.WAREHOUSE, false, 20, 5);
                assertTrue(missing.items().getFirst().reasons().stream()
                        .anyMatch(x -> x.startsWith("READ_KEY_INCOMPLETE")));
            });
            verify(f.queries, never()).readWarehouse(any(), any(), any());
            warehouse.put("owner", "TEST_PLANT").put("plant", "SITE_A");
            f.warehouses(warehouse);
            E10OperationBusinessResponse<WarehouseContract.ReadResponseData> wh = mock(E10OperationBusinessResponse.class);
            when(wh.getRecords()).thenReturn(List.of(JSON.convertValue(warehouse, WarehouseContract.ReadResponseData.class)));
            when(f.queries.readWarehouse("READ_WH", "TEST_PLANT", "SITE_A")).thenReturn(wh);
            f.admitted(() -> assertEquals(1, f.service.execute(Kind.WAREHOUSE, true, 20, 5).created()));
        }
    }

    @Test
    void nullSourceCancelledAndExecutionFactsStayProtected() throws Exception {
        try (var f = new Fixture()) {
            f.admitted(() -> {
                f.db.update("UPDATE workorder SET source=NULL WHERE id=1");
                assertEquals("CONFLICT", f.mapping.prepare(Kind.WORKORDER, raw("WO1"), rule(true)).item().action());
                f.orders(raw("FROZEN"));
                assertEquals(1, f.service.execute(Kind.WORKORDER, true, 20, 5).created());
                long id = f.db.queryForObject("SELECT id FROM workorder WHERE code='FROZEN'", Long.class);
                f.db.update("INSERT INTO mes_production_execution(workorder_id,product_id,routing_id,target_qty,status) VALUES(?,1,1,10,'READY')", id);
                f.orders(raw("FROZEN").deepCopy().withObject("").put("plan_qty", "200"));
                var frozen = f.service.execute(Kind.WORKORDER, true, 20, 5);
                assertEquals(1, frozen.conflicts());
                assertTrue(frozen.items().getFirst().reasons().getFirst().contains("EXECUTION_FROZEN"));
                assertEquals("100.123456", f.db.queryForObject("SELECT planned_qty FROM workorder WHERE id=?", BigDecimal.class, id)
                        .toPlainString());
                assertEquals(1, f.db.queryForObject("SELECT COUNT(*) FROM mes_production_execution WHERE workorder_id=?", Integer.class, id));
            });
        }
    }

    @Test
    void interruptionReportsActualCommittedCountsAndNeverContinuesAfterLoss() throws Exception {
        try (var f = new Fixture()) {
            f.orders(raw("FIRST"), raw("SECOND"));
            doAnswer(inv -> {
                if ((long) inv.getArgument(1) == 1) throw new FoundationException(ErrorCode.SYNC_EXECUTION_LOST);
                return null;
            })
                    .when(f.sync).progress(any(), anyLong());
            f.admitted(() -> {
                var result = f.service.execute(Kind.WORKORDER, true, 20, 5);
                assertEquals("PARTIAL_SUCCESS", result.status());
                assertEquals(1, result.created());
                assertTrue(result.failureReason() != null);
                assertEquals(1, f.db.queryForObject("SELECT COUNT(*) FROM workorder WHERE code='FIRST'", Integer.class));
                assertEquals(0, f.db.queryForObject("SELECT COUNT(*) FROM workorder WHERE code='SECOND'", Integer.class));
            });
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void multiPageAndEmptyPageFinishBeforeWritesAndFactoryBStaysIndependent() throws Exception {
        try (var f = new Fixture()) {
            E10QueryBusinessResponse<MoQueryRow> first = mock(E10QueryBusinessResponse.class), empty = mock(E10QueryBusinessResponse.class);
            when(first.getRows()).thenReturn(List.of(JSON.convertValue(raw("PAGED"), MoQueryRow.class)));
            when(empty.getRows()).thenReturn(List.of());
            when(f.queries.queryMo(any())).thenReturn(first, empty);
            f.admitted(() -> assertEquals(1, f.service.execute(Kind.WORKORDER, true, 1, 2).created()));
            when(f.queries.queryMo(any())).thenReturn(empty);
            f.admitted(() -> assertEquals(0, f.service.execute(Kind.WORKORDER, true, 20, 5).fetched()));
            FactoryIntegrationContext.run(FactorySnapshotFixture.erp("B", "DIGIWIN_E10", "TEST_BINDING_B"), () -> {
                assertThrows(FoundationException.class, () -> f.service.execute(Kind.WORKORDER, true, 20, 5));
            });
            assertEquals(0, f.context.getBean(MaterialIssueHttpTest.Fixture.class).business.get("B")
                    .queryForObject("SELECT COUNT(*) FROM workorder WHERE code='PAGED'", Integer.class));
        }
    }
}
