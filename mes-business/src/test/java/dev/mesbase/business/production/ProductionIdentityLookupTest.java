package dev.mesbase.business.production;

import dev.mesbase.business.basic.dao.MaterialMapper;
import dev.mesbase.business.production.dao.MesProductionCombinationMapper;
import dev.mesbase.business.production.dao.MesProductionExecutionMapper;
import dev.mesbase.business.production.dao.RoutingMapper;
import dev.mesbase.business.production.dao.WorkorderMapper;
import dev.mesbase.business.production.domain.MesProductionExecutionEntity;
import dev.mesbase.business.production.domain.MesProductionExecutionExample;
import dev.mesbase.business.production.service.ProductionIdentityService;
import dev.mesbase.core.error.ErrorCode;
import dev.mesbase.core.error.FoundationException;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ProductionIdentityLookupTest {
    private static ProductionIdentityService service(MesProductionExecutionMapper executions) {
        return new ProductionIdentityService(executions, mock(WorkorderMapper.class), mock(MaterialMapper.class),
                mock(RoutingMapper.class), mock(MesProductionCombinationMapper.class));
    }

    private static MesProductionExecutionEntity execution(long id, String batch) {
        var row = new MesProductionExecutionEntity();
        row.setId(id);
        row.setWorkorderId(10L);
        row.setProductId(20L);
        row.setBatchNo(batch);
        return row;
    }

    @Test
    void exactBatchBeyondOnePageOfCollationNeighborsIsNotHiddenByAQueryLimit() {
        var mapper = mock(MesProductionExecutionMapper.class);
        var approximate = IntStream.rangeClosed(1, 100).mapToObj(id -> execution(id, "LOT")).toList();
        var exact = execution(101, "Lot");
        when(mapper.selectByExample(any(MesProductionExecutionExample.class)))
                .thenReturn(approximate, List.of(exact), List.of());
        assertEquals(101L, service(mapper).findExecution(10L, 20L, "Lot").getId());
    }

    @Test
    void duplicateExactBusinessKeysAreRejectedRatherThanSelectingTheFirstExecution() {
        var mapper = mock(MesProductionExecutionMapper.class);
        when(mapper.selectByExample(any(MesProductionExecutionExample.class)))
                .thenReturn(List.of(execution(1, "001 "), execution(2, "001 ")));
        var failure = assertThrows(FoundationException.class, () -> service(mapper).findExecution(10L, 20L, "001 "));
        assertEquals(ErrorCode.PRODUCTION_IDENTITY_AMBIGUOUS, failure.code());
    }

    @Test
    void requiredBatchReadsOnlyTheExecutionPropertyAndPreservesItsOriginalText() {
        var mapper = mock(MesProductionExecutionMapper.class);
        var row = execution(1, null);
        when(mapper.selectByPrimaryKey(1L)).thenReturn(row);
        var identities = service(mapper);
        assertEquals(ErrorCode.PRODUCTION_BATCH_REQUIRED,
                assertThrows(FoundationException.class, () -> identities.requiredBatch(1L)).code());
        row.setBatchNo(" 001 ");
        assertEquals(" 001 ", identities.requiredBatch(1L));
    }
}
