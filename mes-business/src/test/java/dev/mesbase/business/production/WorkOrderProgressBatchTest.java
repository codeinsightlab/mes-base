package dev.mesbase.business.production;

import com.github.pagehelper.PageHelper;
import dev.mesbase.business.production.dao.WorkOrderProgressExtraMapper;
import dev.mesbase.business.production.domain.WorkOrderProgress.Order;
import dev.mesbase.business.production.domain.WorkOrderProgress.Quality;
import dev.mesbase.business.production.domain.WorkOrderProgress.Query;
import dev.mesbase.business.production.service.WorkOrderProgressQueryService;
import dev.mesbase.business.quality.domain.QualityTreatmentModels.CurrentFact;
import dev.mesbase.business.quality.domain.QualityTreatmentModels.Declaration;
import dev.mesbase.business.quality.service.QualityTreatmentService;
import java.math.BigDecimal;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WorkOrderProgressBatchTest {
    @Test
    void manyExecutionsUsePageWideChunksAndKeepTheLastDeclaration() {
        var mapper = mock(WorkOrderProgressExtraMapper.class);
        var treatments = mock(QualityTreatmentService.class);
        var orders = IntStream.range(1, 26).mapToObj(id -> new Order((long) id, "WO" + id,
                "LOCAL", "RELEASED", "P", "Product", "PCS", new BigDecimal("1000"), null)).toList();
        var references = IntStream.range(0, 751).mapToObj(id -> new Quality((long) (id % 25 + 1),
                (long) id, "d" + id, 1, "e" + id, 11L, BigDecimal.ONE, "CREATE", "RUNNING")).toList();
        when(mapper.orders(any())).thenReturn(orders);
        when(mapper.completions(anyList())).thenReturn(List.of());
        when(mapper.quality(anyList())).thenAnswer(invocation -> {
            var page = PageHelper.getLocalPage();
            int from = (int) page.getStartRow();
            return references.subList(from, Math.min(from + page.getPageSize(), references.size()));
        });
        when(treatments.interpretFacts(anyList())).thenAnswer(invocation -> {
            List<Declaration> declarations = invocation.getArgument(0);
            return declarations.stream().map(d -> new CurrentFact(d.declarationId(), d.revision(),
                    d.executionCode(), d.routingNodeId(), d.defectQty(), d.recordAction(), 0,
                    null, null, null, BigDecimal.ZERO, BigDecimal.ZERO, 0, 0)).toList();
        });
        var result = new WorkOrderProgressQueryService(mapper, treatments)
                .page(new Query(null, null, "ALL", 0, 100));
        assertEquals(25, result.items().size());
        assertEquals(0, new BigDecimal("31").compareTo(result.items().getFirst().qualityUnverifiedQty()));
        assertEquals(0, new BigDecimal("30").compareTo(result.items().getLast().qualityUnverifiedQty()));
        verify(mapper, times(4)).quality(anyList());
        verify(treatments, times(4)).interpretFacts(anyList());
        assertEquals(null, PageHelper.getLocalPage());
    }
}
