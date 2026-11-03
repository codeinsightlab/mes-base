package dev.mesbase.business.production;

import dev.mesbase.business.basic.dao.MaterialMapper;
import dev.mesbase.business.basic.domain.MaterialEntity;
import dev.mesbase.business.production.dao.MesProductionExecutionMapper;
import dev.mesbase.business.production.dao.RoutingMapper;
import dev.mesbase.business.production.dao.WorkorderMapper;
import dev.mesbase.business.production.domain.MesProductionExecutionEntity;
import dev.mesbase.business.production.domain.WorkorderEntity;
import dev.mesbase.business.production.service.ProductionIdentityService;
import dev.mesbase.core.error.ErrorCode;
import dev.mesbase.core.error.FoundationException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class ProductionIdentityLookupTest {
    @Test
    void normalizedCodeDirectlySelectsExecutionWithoutPlanningTableOrIdHop() {
        var mapper = mock(MesProductionExecutionMapper.class);
        var orders = mock(WorkorderMapper.class);
        var materials = mock(MaterialMapper.class);
        var row = new MesProductionExecutionEntity();
        row.setId(17L);
        row.setExecutionCode("WO:A#001$B#002");
        row.setWorkorderId(11L);
        row.setProductId(20L);
        row.setBatchNo(" 001 ");
        var order = new WorkorderEntity();
        order.setId(11L);
        order.setProductId(20L);
        order.setCode("WO");
        var material = new MaterialEntity();
        material.setId(20L);
        material.setCode("OUT");
        when(mapper.selectByCode("WO:A#001$B#002")).thenReturn(row);
        when(orders.selectByPrimaryKey(11L)).thenReturn(order);
        when(materials.selectByPrimaryKey(20L)).thenReturn(material);
        var service = new ProductionIdentityService(mapper, orders, materials, mock(RoutingMapper.class));
        var result = service.resolve("WO:B#002$A#001");
        assertEquals(row.getExecutionCode(), result.executionCode());
        assertEquals(row.getExecutionCode(), result.qrCode());
        assertEquals(" 001 ", result.batchNo());
        verify(mapper, never()).selectByPrimaryKey(anyLong());
        assertEquals(ErrorCode.EXECUTION_NOT_FOUND,
                assertThrows(FoundationException.class, () -> service.resolve("WO:A#001$B#002 ")).code());
    }

    @Test
    void requiredBatchReadsOnlyTheExecutionPropertyAndPreservesOriginalText() {
        var mapper = mock(MesProductionExecutionMapper.class);
        var row = new MesProductionExecutionEntity();
        row.setExecutionCode("WO:A#001");
        when(mapper.selectByCode(row.getExecutionCode())).thenReturn(row);
        var service = new ProductionIdentityService(mapper, mock(WorkorderMapper.class), mock(MaterialMapper.class),
                mock(RoutingMapper.class));
        assertEquals(ErrorCode.PRODUCTION_BATCH_REQUIRED,
                assertThrows(FoundationException.class, () -> service.requiredBatch(row.getExecutionCode())).code());
        row.setBatchNo(" 001 ");
        assertEquals(" 001 ", service.requiredBatch(row.getExecutionCode()));
    }
}
