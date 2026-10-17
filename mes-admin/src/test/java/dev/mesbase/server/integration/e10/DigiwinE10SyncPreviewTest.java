package dev.mesbase.server.integration.e10;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.lixin.capability.digiwin.e10.response.E10QueryBusinessResponse;
import com.lixin.capability.digiwin.e10.response.cms.WarehouseQueryRow;
import com.lixin.capability.digiwin.e10.response.mo.MoQueryRow;
import dev.mesbase.core.error.ErrorCode;
import dev.mesbase.core.error.FoundationException;
import dev.mesbase.core.factory.FactoryId;
import dev.mesbase.core.factory.FactoryRuntimeMode;
import dev.mesbase.core.factory.FactoryScope;
import dev.mesbase.framework.sync.SyncCoordinator;
import dev.mesbase.platform.execution.FactoryIntegrationContext;
import dev.mesbase.platform.execution.FactoryIntegrationSnapshot;
import dev.mesbase.server.integration.e10.DigiwinE10SyncPreview.Kind;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * TEST ONLY: 候选预览，不证明真实 ERP、稳定身份或同步落库。
 */
class DigiwinE10SyncPreviewTest {
    private final DigiwinE10MoQueries queries = mock(DigiwinE10MoQueries.class);
    private final SyncCoordinator coordinator = mock(SyncCoordinator.class);
    private final FactoryId factory = new FactoryId("TEST_ONLY_A");
    private final SyncCoordinator.Run run = new SyncCoordinator.Run(new FactoryScope(factory), "e10.mo.preview", "TEST_RUN");
    private final DigiwinE10SyncPreview service = new DigiwinE10SyncPreview(queries, coordinator);

    @BeforeEach
    void setup() {
        when(coordinator.claim(eq(run.scope()), any(String.class), any(Duration.class))).thenReturn(run);
    }

    private void admitted(Runnable action) {
        FactoryIntegrationContext.run(new FactoryIntegrationSnapshot(factory, FactoryRuntimeMode.ERP,
                DigiwinE10MoQueries.PROVIDER, "TEST_BINDING", 1, 2L), action);
    }

    @SuppressWarnings("unchecked")
    private void orders(List<MoQueryRow> rows) {
        E10QueryBusinessResponse<MoQueryRow> response = mock(E10QueryBusinessResponse.class);
        when(response.getRows()).thenReturn(rows);
        when(queries.queryMo(any())).thenReturn(response);
    }

    @Test
    void capabilityNeverReadsErpAndLocalOrOtherProviderCannotClaim() {
        admitted(() -> {
            assertTrue(service.capability(Kind.WORKORDER).supported());
            assertFalse(service.capability(Kind.WORKORDER).writeEnabled());
        });
        for (var snapshot : List.of(
                new FactoryIntegrationSnapshot(factory, FactoryRuntimeMode.LOCAL, null, null, 1, null),
                new FactoryIntegrationSnapshot(factory, FactoryRuntimeMode.ERP, "OTHER", "OTHER", 1, 1L))) {
            FactoryIntegrationContext.run(snapshot, () -> assertThrows(FoundationException.class,
                    () -> service.preview(Kind.WORKORDER, 1, 20)));
        }
        verifyNoInteractions(queries, coordinator);
    }

    @Test
    void emptyPageRemainsPreviewWithZeroWritesAndNoWatermark() {
        orders(List.of());
        admitted(() -> {
            var result = service.preview(Kind.WORKORDER, 2, 20);
            assertEquals("PREVIEW_ONLY", result.status());
            assertEquals(0, result.fetched());
            assertEquals(0, result.created() + result.updated());
            assertFalse(result.possiblyMore());
            assertFalse(result.blockers().isEmpty());
            assertEquals("2", result.configurationVersion());
        });
        verify(coordinator).succeed(run, null);
        verify(queries).queryMo(any());
    }

    @Test
    void duplicateCodesAndUnknownStatusRemainCandidatesWithExactQuantity() throws Exception {
        var row = MoQueryRow.from(new ObjectMapper().enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
                .readTree("""
                        {"doc_no":"SAME","plan_qty":999999999999.123456,"status":"UNCONFIRMED",
                        "item_no":"PART","unit_no":"EA","process_no":"ROUTE","om_site_id":"SITE"}
                        """));
        orders(List.of(row, row));
        admitted(() -> {
            var result = service.preview(Kind.WORKORDER, 1, 2);
            assertEquals(2, result.fetched());
            assertTrue(result.possiblyMore());
            assertEquals("999999999999.123456", result.candidates().getFirst().plannedQty());
            assertEquals("UNCONFIRMED", result.candidates().getFirst().externalStatus());
            assertEquals("SITE", result.candidates().getFirst().site());
            assertEquals(0, result.skipped());
        });
    }

    @Test
    @SuppressWarnings("unchecked")
    void warehouseDoesNotInferOwnerOrgOrStatusFromSiteOrApproval() throws Exception {
        E10QueryBusinessResponse<WarehouseQueryRow> response = mock(E10QueryBusinessResponse.class);
        when(response.getRows()).thenReturn(List.of(WarehouseQueryRow.from(new ObjectMapper().readTree("""
                {"warehouse_no":"WH","warehouse_name":"仓库","om_site_id":"SITE","approve_status":"N"}
                """))));
        when(queries.queryWarehouse(any())).thenReturn(response);
        admitted(() -> {
            var row = service.preview(Kind.WAREHOUSE, 1, 20).candidates().getFirst();
            assertEquals("WH", row.code());
            assertEquals("SITE", row.site());
            assertEquals(null, row.ownerOrg());
            assertEquals("N", row.externalStatus());
        });
    }

    @Test
    void queryFailureIsRecordedAndPropagatedWithoutSuccessOrRetry() {
        var failure = new IllegalStateException("TEST_ONLY_FAILURE");
        when(queries.queryMo(any())).thenThrow(failure);
        admitted(() -> assertSame(failure, assertThrows(IllegalStateException.class,
                () -> service.preview(Kind.WORKORDER, 1, 20))));
        verify(coordinator).fail(run);
        verify(coordinator, never()).succeed(any(), any());
        verify(queries).queryMo(any());
    }

    @Test
    void lostTokenStopsBeforePublishingPreviewAndPreservesOriginalFailure() {
        orders(List.of());
        doThrow(new FoundationException(ErrorCode.SYNC_EXECUTION_LOST)).when(coordinator).assertOwner(run);
        doThrow(new IllegalStateException("TEST_ONLY_DELIVERY_FAILURE")).when(coordinator).fail(run);
        admitted(() -> {
            var failure = assertThrows(FoundationException.class, () -> service.preview(Kind.WORKORDER, 1, 20));
            assertEquals(ErrorCode.SYNC_EXECUTION_LOST, failure.code());
            assertEquals(1, failure.getSuppressed().length);
        });
        verify(coordinator, never()).succeed(any(), isNull());
    }

    @Test
    void invalidPageAndClaimConflictNeverReadErp() {
        admitted(() -> {
            for (int size : List.of(0, 101))
                assertThrows(FoundationException.class, () -> service.preview(Kind.WORKORDER, 1, size));
            assertThrows(FoundationException.class, () -> service.preview(Kind.WORKORDER, 10001, 20));
            when(coordinator.claim(any(), any(), any())).thenThrow(new FoundationException(ErrorCode.SYNC_RUNNING));
            assertThrows(FoundationException.class, () -> service.preview(Kind.WORKORDER, 1, 20));
        });
        verifyNoInteractions(queries);
        verify(coordinator, never()).fail(any());
    }

    @Test
    void oversizedResponseCannotBeReportedAsSuccessfulPreview() throws Exception {
        var row = MoQueryRow.from(new ObjectMapper().readTree("{\"doc_no\":\"TEST_ONLY\"}"));
        orders(List.of(row, row));
        admitted(() -> assertThrows(FoundationException.class, () -> service.preview(Kind.WORKORDER, 1, 1)));
        verify(coordinator).fail(run);
        verify(coordinator, never()).succeed(any(), any());
    }
}
