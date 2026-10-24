package dev.mesbase.business.quality.service;

import com.github.pagehelper.PageHelper;
import dev.mesbase.business.directory.service.BusinessDirectoryService;
import dev.mesbase.business.production.domain.ProductionClosureRules;
import dev.mesbase.business.production.service.ExecutionPathService;
import dev.mesbase.business.production.service.ProductionChainLocks;
import dev.mesbase.business.production.service.ProductionQuantityService;
import dev.mesbase.business.quality.dao.MesProductionDefectMapper;
import dev.mesbase.business.quality.dao.MesQualityTreatmentMapper;
import dev.mesbase.business.quality.dao.ProductionDefectListExtraMapper;
import dev.mesbase.business.quality.domain.QualityTreatmentModels.CurrentFact;
import dev.mesbase.business.quality.domain.QualityTreatmentModels.RequestKind;
import dev.mesbase.business.quality.domain.QualityTreatmentModels.State;
import dev.mesbase.core.error.FoundationException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import dev.mesbase.common.utils.SecurityUtils;
import dev.mesbase.business.quality.domain.MesProductionDefectEntity;
import dev.mesbase.business.quality.domain.MesQualityTreatmentEntity;
import dev.mesbase.business.quality.domain.QualityTreatmentModels.Disposition;
import dev.mesbase.business.quality.domain.QualityTreatmentModels.Destination;
import dev.mesbase.business.quality.domain.QualityTreatmentModels.Action;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.times;

class QualityTreatmentProjectionTest {
    private final MesProductionDefectMapper defects = mock(MesProductionDefectMapper.class);
    private final MesQualityTreatmentMapper events = mock(MesQualityTreatmentMapper.class);
    private final ProductionDefectListExtraMapper mapper = mock(ProductionDefectListExtraMapper.class);
    private final BusinessDirectoryService directory = mock(BusinessDirectoryService.class);
    private final QualityTreatmentService service = new QualityTreatmentService(defects, events,
            mock(ProductionChainLocks.class), mock(ExecutionPathService.class),
            mock(ProductionQuantityService.class), directory, mapper);

    static CurrentFact fact(String id, long execution, long assessments, String attribution, long invalid) {
        return new CurrentFact(id, 1, execution, 11L, BigDecimal.TEN, "CREATE", assessments,
                attribution, null, "证据", BigDecimal.ZERO, BigDecimal.ZERO, invalid, 0);
    }

    @ParameterizedTest
    @ValueSource(ints = {999, 1000, 1001})
    void legalCurrentDeclarationsAreNotLimitedByHistoryBudget(int count) {
        var facts = IntStream.range(0, count).mapToObj(i -> fact("d" + i, 1, 1, "INDEPENDENT", 0)).toList();
        when(mapper.selectCurrentFacts(List.of(1L), null)).thenReturn(facts);
        assertEquals(0, BigDecimal.TEN.multiply(BigDecimal.valueOf(count))
                .compareTo(service.nodeTotals(1L).getFirst().directDefectQty()));
        verifyNoInteractions(events, defects, directory);
    }

    @Test
    void batchHasNoSharedDeclarationBudgetAndMatchesSingles() {
        var first = IntStream.range(0, 600).mapToObj(i -> fact("a" + i, 1, 1, "INDEPENDENT", 0)).toList();
        var second = IntStream.range(0, 600).mapToObj(i -> fact("b" + i, 2, 0, null, 0)).toList();
        when(mapper.selectCurrentFacts(List.of(1L), null)).thenReturn(first);
        when(mapper.selectCurrentFacts(List.of(2L), null)).thenReturn(second);
        when(mapper.selectCurrentFacts(List.of(1L, 2L), null)).thenReturn(
                Stream.concat(first.stream(), second.stream()).toList());
        var batch = service.nodeTotalsBatch(List.of(1L, 2L));
        assertEquals(service.nodeTotals(1L), batch.get(1L));
        assertEquals(service.nodeTotals(2L), batch.get(2L));
    }

    @Test
    void unknownAndDuplicateFactsAreReadableButStrictConsumersReject() {
        for (var fact : List.of(fact("unknown", 1, 1, "INVALID", 0),
                fact("duplicate", 1, 2, "INDEPENDENT", 0), fact("identity", 1, 1, "INDEPENDENT", 1))) {
            assertEquals(State.UNRESOLVED, fact.summary(false).state());
            assertNull(fact.summary(false).pendingDispositionQty());
            assertNull(fact.summary(false).netDefectQty());
            if (fact.assessmentCount() > 1) {
                assertNull(fact.summary(false).attribution());
                assertNull(fact.summary(false).relatedDeclarationId());
                assertNull(fact.summary(false).basis());
            }
            assertThrows(FoundationException.class, () -> fact.summary(true));
        }
    }

    @Test
    void summaryDoesNotReadDisplayHistoryOrDirectoryAndDatabaseFailureEscapes() {
        when(mapper.selectCurrentFacts(null, "d")).thenReturn(List.of(fact("d", 1, 1, "INDEPENDENT", 0)));
        assertEquals(State.PENDING, service.details("d").summary().state());
        verifyNoInteractions(events, directory, defects);
        when(mapper.selectCurrentFacts(null, "d")).thenThrow(new IllegalStateException("database unavailable"));
        assertThrows(IllegalStateException.class, () -> service.details("d"));
    }

    @Test
    void receiptClosureStillRequiresUniqueFormalProofAndPropagatesFailures() {
        var closed = new CurrentFact("d", 1, 1L, 11L, BigDecimal.TEN, "CREATE", 1,
                "INDEPENDENT", null, "证据", BigDecimal.ZERO, BigDecimal.TEN, 0, 0);
        when(mapper.selectCurrentFacts(List.of(1L), null)).thenReturn(List.of(closed));
        when(mapper.invalidCurrentReceipts(1L)).thenReturn(1L);
        assertTrue(service.receiptBlockReason(1L).contains("唯一有效"));
        when(mapper.invalidCurrentReceipts(1L)).thenReturn(0L);
        assertNull(service.receiptBlockReason(1L));
        when(mapper.invalidCurrentReceipts(1L)).thenThrow(new IllegalStateException("database unavailable"));
        assertThrows(IllegalStateException.class, () -> service.receiptBlockReason(1L));
    }

    @Test
    void pagedAuditIdentitiesKeepTheOriginalJsonAndDigest() {
        when(mapper.selectCurrentFacts(List.of(1L), null)).thenReturn(List.of());
        var original = IntStream.range(0, 1201).mapToObj(i -> "T:" + String.format("%05d", i)).toList();
        when(mapper.selectFactIdentities(1L)).thenAnswer(invocation -> {
            var page = PageHelper.getLocalPage();
            return original.subList((int) page.getStartRow(), Math.min(original.size(), (int) page.getStartRow() + page.getPageSize()));
        });
        assertEquals(ProductionClosureRules.hash("same", original),
                ProductionClosureRules.hash("same", service.productionBasis(1L).factIdentities()));
        verify(mapper, times(3)).selectFactIdentities(1L);
    }

    @Test
    void historicalDispositionAndCurrentOverlapStillProtectRevisionCommands() {
        var before = new MesProductionDefectEntity();
        before.setDeclarationId("target");
        before.setExecutionId(1L);
        when(events.countByExample(any())).thenReturn(1L);
        assertThrows(FoundationException.class, () -> service.assertMutable(before));
        verifyNoInteractions(mapper);
        when(events.countByExample(any())).thenReturn(0L);
        var overlap = new CurrentFact("dependent", 7, 1L, 11L, BigDecimal.ONE, "REVISE", 1,
                "OVERLAP", "target", "证据", BigDecimal.ZERO, BigDecimal.ZERO, 0, 0);
        when(mapper.selectCurrentFacts(List.of(1L), null)).thenReturn(List.of(overlap));
        assertThrows(FoundationException.class, () -> service.assertMutable(before));
        when(mapper.selectCurrentFacts(List.of(1L), null)).thenReturn(List.of());
        service.assertMutable(before);
    }

    @Test
    void exactRequestChecksActorActionDeclarationAndOriginalContent() {
        var id = UUID.randomUUID().toString();
        var command = new Disposition(id, 1, Destination.WAREHOUSE, BigDecimal.ONE, 3L, "原内容");
        var check = new QualityTreatmentService.RequestCheck(RequestKind.DISPOSE, null, command);
        try (var security = mockStatic(SecurityUtils.class)) {
            security.when(SecurityUtils::getUserId).thenReturn(1L);
            var event = new MesQualityTreatmentEntity();
            event.setId(id);
            event.setDeclarationId("d");
            event.setActionType(Action.TRANSFER_WAREHOUSE.getValue());
            event.setRequestHash(QualityDispositionService.hash("d", command));
            event.setOperatorUserId(2L);
            when(events.selectByPrimaryKey(id)).thenReturn(event);
            assertThrows(FoundationException.class, () -> service.checkRequest("d", check));
            event.setOperatorUserId(1L);
            assertEquals(event, service.checkRequest("d", check));
            event.setActionType(Action.ASSESS.getValue());
            assertThrows(FoundationException.class, () -> service.checkRequest("d", check));
            event.setActionType(Action.TRANSFER_WAREHOUSE.getValue());
            assertThrows(FoundationException.class, () -> service.checkRequest("other", check));
        }
    }

    @Test
    void historyUsesOnlyPageOperatorsAndNeverCurrentQuantityProjection() {
        var declaration = new MesProductionDefectEntity();
        declaration.setDeclarationId("d");
        when(defects.selectByExample(any())).thenReturn(List.of(declaration));
        var rows = IntStream.range(0, 100).mapToObj(i -> {
            var event = new MesQualityTreatmentEntity();
            event.setOperatorUserId((long) i);
            return event;
        }).toList();
        when(events.selectByExample(any())).thenReturn(rows);
        var operators = IntStream.range(0, 100).mapToObj(i -> (long) i).toList();
        when(directory.userLabels(operators)).thenReturn(Map.of());
        assertEquals(100, service.history("d", 40, 100).items().size());
        verify(directory).userLabels(operators);
        verifyNoInteractions(mapper);
        assertThrows(FoundationException.class, () -> service.history("d", 0, 101));
    }

    @AfterEach
    void noPaginationLeaks() {
        assertNull(PageHelper.getLocalPage());
    }
}
