package dev.mesbase.business.production;

import dev.mesbase.business.basic.dao.MaterialMapper;
import dev.mesbase.business.basic.domain.MaterialEntity;
import dev.mesbase.business.basic.domain.MaterialExample;
import dev.mesbase.business.directory.service.BusinessDirectoryService;
import dev.mesbase.business.production.controller.MesProductionExecutionController;
import dev.mesbase.business.production.dao.WorkorderMapper;
import dev.mesbase.business.production.domain.MesProductionExecutionEntity;
import dev.mesbase.business.production.domain.MesProductionExecutionPage;
import dev.mesbase.business.production.domain.MesProductionExecutionQueryDto;
import dev.mesbase.business.production.domain.WorkorderEntity;
import dev.mesbase.business.production.service.IMesProductionExecutionService;
import dev.mesbase.business.production.service.ProductionExecutionQueryService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ProductionExecutionQueryServiceTest {
    @Test
    void batchReadsDistinctProductsAndExposesCodesWithoutChangingStoredIdsOrPagination() {
        var executions = mock(IMesProductionExecutionService.class);
        var directory = mock(BusinessDirectoryService.class);
        var orders = mock(WorkorderMapper.class);
        var materials = mock(MaterialMapper.class);
        var service = new ProductionExecutionQueryService(executions, directory, orders, materials);
        var first = execution(1L, 901L);
        var second = execution(2L, 901L);
        var missing = execution(3L, 902L);
        var order = new WorkorderEntity();
        order.setId(10L);
        order.setStatus("RELEASED");
        var product = new MaterialEntity();
        product.setId(901L);
        product.setCode("P-001");
        product.setName("产出物料");
        when(executions.selectPage(any())).thenReturn(new MesProductionExecutionPage(List.of(first, second, missing), 80, 20, 3));
        when(orders.selectByExample(any())).thenReturn(List.of(order));
        when(materials.selectByExample(any())).thenReturn(List.of(product));
        var result = new MesProductionExecutionController(executions, service).list(new MesProductionExecutionQueryDto(null, null, null, null, null, null, null, null, null, null, null));
        assertEquals(80, result.total());
        assertEquals(20, result.offset());
        assertEquals(3, result.limit());
        assertEquals("901", result.items().getFirst().productId());
        assertEquals("P-001", result.items().getFirst().productCode());
        assertEquals("产出物料", result.items().getFirst().productName());
        assertEquals("P-001", result.items().get(1).productCode());
        assertNull(result.items().get(2).productCode());
        var filter = ArgumentCaptor.forClass(MaterialExample.class);
        verify(materials).selectByExample(filter.capture());
        assertEquals(List.of(901L, 902L), filter.getValue().getOredCriteria().getFirst().getAllCriteria().getFirst().getValue());
    }

    @Test
    void emptyPageDoesNotIssueReferenceReads() {
        var executions = mock(IMesProductionExecutionService.class);
        var directory = mock(BusinessDirectoryService.class);
        var orders = mock(WorkorderMapper.class);
        var materials = mock(MaterialMapper.class);
        when(executions.selectPage(any())).thenReturn(new MesProductionExecutionPage(List.of(), 0, 0, 20));
        var service = new ProductionExecutionQueryService(executions, directory, orders, materials);
        assertEquals(List.of(), service.page(new MesProductionExecutionQueryDto(null, null, null, null, null, null, null, null, null, null, null)).records().items());
        verifyNoInteractions(materials, orders, directory);
    }

    private static MesProductionExecutionEntity execution(Long id, Long productId) {
        var row = new MesProductionExecutionEntity();
        row.setId(id);
        row.setProductId(productId);
        row.setWorkorderId(10L);
        row.setRoutingId(20L);
        return row;
    }
}
