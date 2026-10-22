package dev.mesbase.common.bean;

import dev.mesbase.common.core.page.PageData;
import dev.mesbase.common.utils.bean.BeanConversions;
import dev.mesbase.business.basic.domain.MaterialEntity;
import dev.mesbase.business.basic.domain.MaterialPage;
import dev.mesbase.business.basic.controller.vo.MaterialPageVo;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.core.JacksonException;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BeanConversionsTest {
    record Source(Long id, BigDecimal quantity, String password, List<Source> children, LocalDateTime time) { }
    record View(String id, String quantity, List<View> children, LocalDateTime time) { }
    record QueryPage(List<Source> items, long total, int offset, int limit) implements PageData<Source> { }
    record ViewPage(List<View> items, long total, int offset, int limit) implements PageData<View> { }
    record Required(int count) { }

    @Test
    void mapsImmutableRecordsAndNestedGenericRowsWithoutLosingPrecision() {
        var time = LocalDateTime.of(2026, 10, 22, 10, 30, 0, 123456000);
        var child = new Source(9007199254740993L, new BigDecimal("1E+18"), "TEST ONLY secret", null, time);
        var source = new Source(9007199254740995L, new BigDecimal("12345678901234.123456"), "TEST ONLY secret", List.of(child), time);
        var page = BeanConversions.convertPage(new QueryPage(List.of(source), 123, 99, 20), ViewPage.class);
        assertEquals(123, page.total());
        assertEquals(99, page.offset());
        assertEquals(20, page.limit());
        var row = page.items().getFirst();
        assertEquals("9007199254740995", row.id());
        assertEquals("12345678901234.123456", row.quantity());
        assertEquals("1000000000000000000", row.children().getFirst().quantity());
        assertEquals(time, row.time());
        assertEquals(4, View.class.getRecordComponents().length); // password has no target field.
        assertNull(row.children().getFirst().children());
    }

    @Test
    void keepsTheExistingGeneratedQueryAndResponseContracts() {
        var entity = new MaterialEntity();
        entity.setId(9007199254740993L);
        entity.setCode("TEST ONLY");
        var page = BeanConversions.convertPage(new MaterialPage(List.of(entity), 7, 3, 2), MaterialPageVo.class);
        assertEquals("9007199254740993", page.items().getFirst().id());
        assertNull(page.items().getFirst().unitId());
        assertEquals(7, page.total());
        assertEquals(3, page.offset());
        assertEquals(2, page.limit());
    }

    @Test
    void preservesEmptyPagesAndNullsAndRejectsInvalidPrimitiveMappings() {
        var page = BeanConversions.convertPage(new QueryPage(List.of(), 0, 100, 20), ViewPage.class);
        assertTrue(page.items().isEmpty());
        assertEquals(100, page.offset());
        assertNull(BeanConversions.convert(null, View.class));
        assertThrows(JacksonException.class, () -> BeanConversions.convert(new RequiredSource(null), Required.class));
    }

    record RequiredSource(Integer count) { }
}
