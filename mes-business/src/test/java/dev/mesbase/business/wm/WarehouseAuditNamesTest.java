package dev.mesbase.business.wm;

import dev.mesbase.business.directory.domain.DirectoryLabel;
import dev.mesbase.business.directory.service.BusinessDirectoryService;
import dev.mesbase.business.wm.domain.WmWarehouseEntity;
import dev.mesbase.business.wm.domain.WmWarehousePage;
import dev.mesbase.business.wm.domain.WmWarehouseQueryDto;
import dev.mesbase.business.wm.service.IWmWarehouseService;
import dev.mesbase.business.wm.service.WarehouseQueryService;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;

class WarehouseAuditNamesTest {
    @Test
    void batchesDistinctAuditActorsAndKeepsOriginalPageAndIds() {
        var warehouses = mock(IWmWarehouseService.class);
        var directory = mock(BusinessDirectoryService.class);
        var row = new WmWarehouseEntity();
        row.setCreateBy("100");
        row.setUpdateBy("101");
        var query = new WmWarehouseQueryDto(20, 20, null, null, null, null);
        var page = new WmWarehousePage(List.of(row, row), 53, 20, 20);
        var labels = Map.of(100L, new DirectoryLabel(100L, "creator", "张三", null),
                101L, new DirectoryLabel(101L, "editor", "李四", null));
        when(warehouses.selectPage(query)).thenReturn(page);
        when(directory.userLabels(List.of(100L, 101L))).thenReturn(labels);
        var result = new WarehouseQueryService(warehouses, directory).page(query);
        assertEquals(page, result.records());
        assertEquals("张三", WarehouseQueryService.name(result.users(), row.getCreateBy()));
        assertEquals("李四", WarehouseQueryService.name(result.users(), row.getUpdateBy()));
        assertEquals("100", row.getCreateBy());
        verify(directory, times(1)).userLabels(List.of(100L, 101L));
    }

    @Test
    void missingOrDeletedUsersDoNotDisplayRawIds() {
        assertNull(WarehouseQueryService.name(Map.of(), null));
        assertEquals("未知用户", WarehouseQueryService.name(Map.of(), "100"));
        assertEquals("未知用户", WarehouseQueryService.name(Map.of(), "9999999999999999999"));
        assertEquals("account", WarehouseQueryService.name(Map.of(1L, new DirectoryLabel(1L, "account", "", null)), "1"));
    }
}
