package dev.mesbase.business.wm;

import dev.mesbase.business.wm.domain.WarehouseType;
import dev.mesbase.business.wm.domain.WmWarehouseEntity;
import dev.mesbase.business.wm.controller.vo.WmWarehouseVo;
import dev.mesbase.common.utils.bean.BeanConversions;
import org.junit.jupiter.api.Test;
import java.util.List;

import dev.mesbase.business.wm.domain.WmWarehouseQueryDto;
import dev.mesbase.infrastructure.cache.BusinessCacheProcessor;
import dev.mesbase.common.core.redis.RedisCache;
import dev.mesbase.infrastructure.persistence.SpringCommitActions;
import org.springframework.core.env.StandardEnvironment;

import static org.mockito.Mockito.mock;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class WarehouseTypeTest {
    @Test
    void stableCodesAndLabelsRoundTripWithoutDefaulting() {
        assertEquals(List.of("RAW_MATERIAL", "WIP", "FINISHED_GOODS", "INSPECTION", "DEFECTIVE", "SCRAP", "OUTSOURCING"), List.copyOf(WarehouseType.toMap()
                .keySet()));
        for (WarehouseType type : WarehouseType.values()) {
            assertSame(type, WarehouseType.from(type.getValue()));
            var row = new WmWarehouseEntity();
            row.setId(9007199254740993L);
            row.setWarehouseType(type.getValue());
            var view = BeanConversions.convert(row, WmWarehouseVo.class);
            assertEquals("9007199254740993", view.id());
            assertEquals(type.getValue(), view.warehouseType());
            assertEquals(type.getDescription(), view.warehouseTypeLabel());
        }
        assertNull(WarehouseType.from(null));
        assertNull(WarehouseType.from("原材料仓"));
        assertNull(WarehouseType.from("raw_material"));
        assertEquals("待补录", BeanConversions.convert(new WmWarehouseEntity(), WmWarehouseVo.class)
                .warehouseTypeLabel());
        var labels = WarehouseType.toMap();
        labels.clear();
        assertEquals(7, WarehouseType.toMap().size());
    }

    @Test
    void typeFilterParticipatesInTheExistingCacheKeyWithoutLosingPagination() {
        var caches = new BusinessCacheProcessor(mock(RedisCache.class), mock(SpringCommitActions.class), new StandardEnvironment());
        var raw = new WmWarehouseQueryDto(0, 20, null, null, null, null, null, WarehouseType.RAW_MATERIAL.getValue());
        var wip = new WmWarehouseQueryDto(0, 20, null, null, null, null, null, WarehouseType.WIP.getValue());
        assertNotEquals(caches.queryHash(new Object[]{raw}, raw), caches.queryHash(new Object[]{wip}, wip));
        assertEquals(0, raw.cacheOffset());
        assertEquals(20, raw.cacheLimit());
        assertEquals("id desc", raw.cacheSort());
    }
}
