package dev.mesbase.business.quality.service;

import com.github.pagehelper.PageHelper;
import dev.mesbase.business.directory.service.BusinessDirectoryService;
import dev.mesbase.business.production.service.ExecutionPathService;
import dev.mesbase.business.production.service.ProductionChainLocks;
import dev.mesbase.business.production.service.ProductionQuantityService;
import dev.mesbase.business.quality.dao.MesProductionDefectMapper;
import dev.mesbase.business.quality.dao.MesQualityTreatmentMapper;
import dev.mesbase.business.quality.domain.DefectRecordAction;
import dev.mesbase.business.quality.domain.MesProductionDefectEntity;
import dev.mesbase.business.quality.domain.MesQualityTreatmentEntity;
import dev.mesbase.business.quality.domain.QualityTreatmentModels.Action;
import dev.mesbase.business.quality.domain.QualityTreatmentModels.Attribution;
import dev.mesbase.core.error.FoundationException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;
import java.util.concurrent.atomic.LongAdder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit projection equivalence and cap tests. Real SQL LIMIT/rollback evidence is recorded separately.
 */
class QualityTreatmentProjectionTest {
    private final MesProductionDefectMapper defects = mock(MesProductionDefectMapper.class);
    private final MesQualityTreatmentMapper events = mock(MesQualityTreatmentMapper.class);
    private final QualityTreatmentService service = new QualityTreatmentService(defects, events,
            mock(ProductionChainLocks.class), mock(ExecutionPathService.class),
            mock(ProductionQuantityService.class), mock(BusinessDirectoryService.class));

    @AfterEach
    void paginationDoesNotEscapeTheProjection() {
        assertNull(PageHelper.getLocalPage());
    }

    private static MesProductionDefectEntity declaration(String id, int revision, String quantity, long node) {
        var d = new MesProductionDefectEntity();
        d.setDeclarationId(id);
        d.setRevisionNo(revision);
        d.setExecutionId(1L);
        d.setAffectedNodeId(node);
        d.setDefectQty(new BigDecimal(quantity));
        d.setRecordAction(DefectRecordAction.CREATE.getValue());
        d.setDestinationType("SAME_NODE");
        return d;
    }

    private static MesQualityTreatmentEntity event(String id, String declaration, int revision, Action action,
                                                   Attribution type, String rework, String processed,
                                                   String recovered, String failed) {
        var e = new MesQualityTreatmentEntity();
        e.setId(id);
        e.setDeclarationId(declaration);
        e.setDeclarationRevision(revision);
        e.setExecutionId(1L);
        e.setActionType(action.name());
        e.setAttributionType(type == null ? null : type.name());
        e.setScrapQty(BigDecimal.ZERO);
        e.setReworkQty(new BigDecimal(rework));
        e.setProcessedQty(new BigDecimal(processed));
        e.setRecoveredQty(new BigDecimal(recovered));
        e.setFailedQty(new BigDecimal(failed));
        return e;
    }

    @Test
    void executionBatchKeepsSharedNodesSeparateAndMatchesSingleProjection() {
        var first = declaration("first", 1, "5", 11);
        var second = declaration("second", 1, "9", 11);
        second.setExecutionId(2L);
        var gone = declaration("gone", 1, "99", 11);
        gone.setExecutionId(2L);
        gone.setRecordAction(DefectRecordAction.VOID.getValue());
        when(events.selectByExample(any())).thenReturn(List.of());
        when(defects.selectLatestBatch(List.of(1L, 2L))).thenReturn(List.of(first, second, gone));
        var result = service.nodeTotalsBatch(List.of(1L, 2L));
        when(defects.selectLatest(1L, null, null)).thenReturn(List.of(first));
        assertEquals(service.nodeTotals(1L), result.get(1L));
        when(defects.selectLatest(2L, null, null)).thenReturn(List.of(second, gone));
        assertEquals(service.nodeTotals(2L), result.get(2L));
    }

    @Test
    void executionBatchCapAppliesBeforeVoidFiltering() {
        var rows = IntStream.range(0, 1001).mapToObj(i -> {
            var d = declaration("void-" + i, 1, "1", 11);
            d.setRecordAction(DefectRecordAction.VOID.getValue());
            return d;
        }).toList();
        when(events.selectByExample(any())).thenReturn(List.of());
        when(defects.selectLatestBatch(any())).thenReturn(rows);
        assertThrows(FoundationException.class, () -> service.nodeTotalsBatch(List.of(1L, 2L)));
    }

    @Test
    void batchMatchesTheExistingSingleDeclarationProjectionIncludingRevisionAndEncounterOrder() {
        var a = declaration("a", 2, "5", 11);
        var b = declaration("b", 1, "2", 11);
        var voided = declaration("void", 1, "99", 12);
        voided.setRecordAction(DefectRecordAction.VOID.getValue());
        var unknown = declaration("unknown", 1, "1", 12);
        var first = event("a2-assess", "a", 2, Action.ASSESS, Attribution.INDEPENDENT, "0", "0", "0", "0");
        var second = event("a2-later-assess", "a", 2, Action.ASSESS, Attribution.OVERLAP, "0", "0", "0", "0");
        var overlap = event("b-assess", "b", 1, Action.ASSESS, Attribution.OVERLAP, "0", "0", "0", "0");
        overlap.setRelatedDeclarationId("a");
        var rows = List.of(
                event("a1-old", "a", 1, Action.ASSESS, Attribution.OVERLAP, "0", "0", "0", "0"),
                first, overlap, second,
                event("a2-allocate1", "a", 2, Action.ALLOCATE, null, "2", "0", "0", "0"),
                event("a2-allocate2", "a", 2, Action.ALLOCATE, null, "3", "0", "0", "0"),
                event("a2-start1", "a", 2, Action.START, null, "2", "0", "0", "0"),
                event("a2-result1", "a", 2, Action.RESULT, null, "0", "2", "1", "1"),
                event("a2-start2", "a", 2, Action.START, null, "3", "0", "0", "0"),
                event("a2-result2", "a", 2, Action.RESULT, null, "0", "3", "3", "0"),
                event("a2-correction", "a", 2, Action.CORRECT_RESULT, null, "0", "0", "-0.5", "0.5"),
                event("void-assess", "void", 1, Action.ASSESS, Attribution.INDEPENDENT, "0", "0", "0", "0"));
        when(events.selectByExample(any())).thenReturn(rows);
        when(defects.selectLatest(1L, null, null)).thenReturn(List.of(a, b, voided, unknown));
        var basis = service.releaseBasis(1L);
        assertEquals(3, basis.declarations().size());
        for (var d : List.of(a, b, unknown)) {
            when(defects.selectByExample(any())).thenReturn(List.of(d));
            var original = service.summary(d.getDeclarationId());
            assertEquals(original, basis.declarations().stream()
                    .filter(s -> s.declarationId().equals(d.getDeclarationId())).findFirst().orElseThrow());
        }
        var projected = basis.declarations().getFirst();
        assertEquals(Attribution.INDEPENDENT, projected.attribution());
        assertEquals(new BigDecimal("3.5"), projected.recoveredQty());
        assertEquals(new BigDecimal("1.5"), projected.netDefectQty());
        assertThrows(UnsupportedOperationException.class, () -> projected.events().add(first));
        assertEquals(15, basis.factIdentities().size()); // 3 current non-VOID declarations + all 12 events.
        var totals = service.nodeTotals(1L);
        var n = totals.stream().filter(v -> v.affectedNodeId().equals(11L)).findFirst().orElseThrow();
        assertEquals(new BigDecimal("7"), n.effectiveDefectQty());
        assertEquals(new BigDecimal("3.5"), n.recoveredQty());
        assertEquals(new BigDecimal("5"), n.directDefectQty());
    }

    @ParameterizedTest
    @ValueSource(ints = {999, 1000, 1001})
    void declarationCapacityIsCheckedBeforeVoidFiltering(int count) {
        var rows = IntStream.range(0, count).mapToObj(i -> declaration("d" + i, 1, "1", 11)).toList();
        rows.forEach(d -> d.setRecordAction(DefectRecordAction.VOID.getValue()));
        when(defects.selectLatest(1L, null, null)).thenReturn(rows);
        when(events.selectByExample(any())).thenReturn(List.of());
        if (count <= 1000) {
            assertEquals(List.of(), service.nodeTotals(1L));
            assertEquals(List.of(), service.releaseBasis(1L).declarations());
        } else {
            assertThrows(FoundationException.class, () -> service.nodeTotals(1L));
            assertThrows(FoundationException.class, () -> service.releaseBasis(1L));
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {4999, 5000, 5001})
    void eventCapacityRemainsBounded(int count) {
        var rows = new ArrayList<MesQualityTreatmentEntity>();
        for (int i = 0; i < count; i++)
            rows.add(event("t" + i, "d", 1, Action.ASSESS, Attribution.INDEPENDENT, "0", "0", "0", "0"));
        when(events.selectByExample(any())).thenReturn(rows);
        when(defects.selectLatest(1L, null, null)).thenReturn(List.of(declaration("d", 1, "1", 11)));
        if (count <= 5000) assertEquals(count + 1, service.releaseBasis(1L).factIdentities().size());
        else assertThrows(FoundationException.class, () -> service.releaseBasis(1L));
    }

    @Test
    void mutableGuardOnlyUsesCurrentRevisionForOverlapButNeverHidesAnyDispositionHistory() {
        var target = declaration("target", 1, "1", 11);
        var related = declaration("related", 2, "1", 11);
        var old = event("old-overlap", "related", 1, Action.ASSESS, Attribution.OVERLAP, "0", "0", "0", "0");
        old.setRelatedDeclarationId("target");
        var current = event("new-independent", "related", 2, Action.ASSESS, Attribution.INDEPENDENT, "0", "0", "0", "0");
        when(defects.selectLatest(1L, null, null)).thenReturn(List.of(target, related));
        when(events.selectByExample(any())).thenReturn(List.of(old, current));
        service.assertMutable(target);
        current.setAttributionType(Attribution.OVERLAP.name());
        current.setRelatedDeclarationId("target");
        assertThrows(FoundationException.class, () -> service.assertMutable(target));
        when(events.selectByExample(any())).thenReturn(List.of(old, event("old-disposition", "target", 1, Action.ALLOCATE, null, "1", "0", "0", "0")));
        assertThrows(FoundationException.class, () -> service.assertMutable(target));
    }

    private static final class CountingTreatment extends MesQualityTreatmentEntity {
        private final LongAdder reads;

        private CountingTreatment(LongAdder reads) {
            this.reads = reads;
        }

        @Override
        public String getDeclarationId() {
            reads.increment();
            return super.getDeclarationId();
        }
    }

    @Test
    void maximumBatchFiltersFiveMillionIdentitiesOnceInsteadOfPerDeclaration() {
        var declarations = IntStream.range(0, 1000)
                .mapToObj(i -> declaration("d" + i, 1, "1", 11)).toList();
        var reads = new LongAdder();
        var rows = new ArrayList<MesQualityTreatmentEntity>();
        for (int i = 0; i < 5000; i++) {
            var e = new CountingTreatment(reads);
            e.setId("event" + i);
            e.setDeclarationId("d" + i / 5);
            e.setDeclarationRevision(1);
            e.setExecutionId(1L);
            e.setRoutingNodeId(11L);
            e.setActionType(Action.ASSESS.name());
            e.setAttributionType(Attribution.INDEPENDENT.name());
            e.setScrapQty(BigDecimal.ZERO);
            e.setReworkQty(BigDecimal.ZERO);
            e.setProcessedQty(BigDecimal.ZERO);
            e.setRecoveredQty(BigDecimal.ZERO);
            e.setFailedQty(BigDecimal.ZERO);
            rows.add(e);
        }
        when(events.selectByExample(any())).thenReturn(rows);
        when(defects.selectLatest(1L, null, null)).thenReturn(declarations);
        var grouped = service.releaseBasis(1L);
        assertEquals(5000L, reads.sum());
        reads.reset();
        for (int i = 0; i < declarations.size(); i++) {
            var d = declarations.get(i);
            when(defects.selectByExample(any())).thenReturn(List.of(d));
            // The unchanged single-declaration API is the original filter/projection reference.
            assertEquals(service.summary(d.getDeclarationId()), grouped.declarations().get(i));
        }
        assertEquals(5_000_000L, reads.sum());
    }

}
