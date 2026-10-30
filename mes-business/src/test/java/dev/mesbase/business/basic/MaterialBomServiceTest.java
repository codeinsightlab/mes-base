package dev.mesbase.business.basic;

import dev.mesbase.business.basic.controller.MaterialBomController;
import dev.mesbase.business.basic.controller.MaterialBomItemController;
import dev.mesbase.business.basic.dao.MaterialBomMapper;
import dev.mesbase.business.basic.dao.MaterialBomItemMapper;
import dev.mesbase.business.basic.dao.MaterialBomCopyMapper;
import dev.mesbase.business.basic.dao.MaterialMapper;
import dev.mesbase.business.basic.dao.UnitMeasureMapper;
import dev.mesbase.business.basic.domain.MaterialBomEntity;
import dev.mesbase.business.basic.domain.MaterialBomExample;
import dev.mesbase.business.basic.domain.MaterialBomItemExample;
import dev.mesbase.business.basic.domain.MaterialBomItemEntity;
import dev.mesbase.business.basic.domain.MaterialBomItemQueryDto;
import dev.mesbase.business.basic.domain.MaterialBomItemUpdateCommand;
import dev.mesbase.business.basic.domain.MaterialBomUpdateCommand;
import dev.mesbase.business.basic.domain.MaterialEntity;
import dev.mesbase.business.basic.domain.UnitMeasureEntity;
import dev.mesbase.business.basic.service.MaterialBomService;
import dev.mesbase.core.error.FoundationException;
import dev.mesbase.core.error.ErrorCode;
import dev.mesbase.platform.web.EndpointAccess;
import java.math.BigDecimal;
import java.lang.reflect.Modifier;
import java.util.List;
import java.util.ArrayList;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.times;

class MaterialBomServiceTest {
    private final MaterialBomMapper boms = mock(MaterialBomMapper.class);
    private final MaterialBomItemMapper items = mock(MaterialBomItemMapper.class);
    private final MaterialMapper materials = mock(MaterialMapper.class);
    private final UnitMeasureMapper units = mock(UnitMeasureMapper.class);
    private final MaterialBomService service = new MaterialBomService(boms, items, mock(MaterialBomCopyMapper.class), materials, units);

    @Test
    void threeLevelRatiosUseEachBaseAndKeepTheDirectComponents() {
        var root = bom(1, 3, "1");
        var sub = bom(2, 2, "2");
        fixture(List.of(root, sub), List.of(line(10, 1, 2, "2", 2L), line(20, 2, 1, "5", null)));
        var graph = service.graph(1L, new BigDecimal("10"));
        assertEquals(new BigDecimal("20.000000"), graph.items().getFirst().quantity());
        assertEquals(new BigDecimal("50.000000"), graph.items().getFirst().children().getFirst().quantity());
        assertEquals(1L, graph.totals().getFirst().materialId());
        assertEquals(new BigDecimal("50.000000"), graph.totals().getFirst().quantity());
        verify(items, never()).insert(any());
    }

    @Test
    void unrelatedCatalogLargerThanBudgetDoesNotPreventSmallGraph() {
        var catalog = new ArrayList<MaterialBomEntity>();
        for (int id = 1; id <= 2500; id++) catalog.add(bom(id, 3, "1"));
        fixture(catalog, List.of(line(10, 1, 1, "2", null)));
        assertEquals(new BigDecimal("2.000000"), service.graph(1L, BigDecimal.ONE).totals().getFirst().quantity());
        verify(boms, never()).countByExample(any());
        verify(items, never()).countByExample(any());
        verify(boms, times(1)).selectByExample(any());
        verify(items, times(1)).selectByExample(any());
    }

    @Test
    void danglingExplicitChildIsRejected() {
        fixture(List.of(bom(1, 3, "1")), List.of(line(10, 1, 2, "1", 999L)));
        assertThrows(FoundationException.class, () -> service.graph(1L, BigDecimal.ONE));
    }

    @Test
    void excessiveReachableFanoutIsRejectedBeforeMoreQueries() {
        var rows = new ArrayList<MaterialBomItemEntity>();
        for (int id = 1; id <= 2001; id++) rows.add(line(id, 1, 2, "1", (long) id + 1));
        fixture(List.of(bom(1, 3, "1")), rows);
        assertThrows(FoundationException.class, () -> service.graph(1L, BigDecimal.ONE));
        verify(boms, times(1)).selectByExample(any());
    }

    @Test
    void excessiveDepthAndLineCountRetainTheirOwnBudgetErrors() {
        var versions = new ArrayList<MaterialBomEntity>();
        var rows = new ArrayList<MaterialBomItemEntity>();
        for (int id = 1; id <= 101; id++) {
            versions.add(bom(id, 3, "1"));
            rows.add(line(id, id, 2, "1", (long) id + 1));
        }
        fixture(versions, rows);
        assertEquals(ErrorCode.MATERIAL_BOM_BUDGET,
                assertThrows(FoundationException.class, () -> service.graph(1L, BigDecimal.ONE)).code());
        rows.clear();
        for (int id = 1; id <= 10001; id++) rows.add(line(id, 1, 2, "1", null));
        fixture(List.of(bom(1, 3, "1")), rows);
        assertEquals(ErrorCode.MATERIAL_BOM_BUDGET,
                assertThrows(FoundationException.class, () -> service.graph(1L, BigDecimal.ONE)).code());
    }

    @Test
    void indirectCycleFailsInsteadOfReturningATruncatedTree() {
        fixture(List.of(bom(1, 3, "1"), bom(2, 2, "1")), List.of(line(10, 1, 2, "1", 2L), line(20, 2, 3, "1", 1L)));
        assertThrows(FoundationException.class, () -> service.graph(1L, BigDecimal.ONE));
    }

    @Test
    void sharedSubassemblyAppearsOnBothPathsAndAggregatesLeaves() {
        fixture(List.of(bom(1, 4, "1"), bom(2, 2, "1"), bom(3, 3, "1")),
                List.of(line(10, 1, 2, "2", 2L), line(11, 1, 3, "3", 3L), line(20, 2, 1, "5", null), line(30, 3, 2, "1", 2L)));
        var graph = service.graph(1L, BigDecimal.ONE);
        assertEquals(2, graph.items().size());
        assertEquals(new BigDecimal("25.000000"), graph.totals().getFirst().quantity());
        assertNotEquals(graph.items().get(0).children().getFirst().path(), graph.items().get(1).children().getFirst()
                .children().getFirst().path());
        verify(boms, times(2)).selectByExample(any());
        verify(items, times(2)).selectByExample(any());
    }

    @Test
    void publishedHeaderAndComponentsCannotBeChangedThroughCrud() {
        var root = bom(1, 3, "1");
        fixture(List.of(root), List.of(line(10, 1, 1, "2.5", null)));
        when(items.selectByPrimaryKey(10L)).thenReturn(line(10, 1, 1, "2.5", null));
        assertThrows(FoundationException.class, () -> service.updateById(1L, new MaterialBomUpdateCommand("x", "v2", BigDecimal.ONE, null, 1L)));
        assertThrows(FoundationException.class, () -> service.updateItem(10L, new MaterialBomItemUpdateCommand(1L, BigDecimal.ONE, null, 0)));
        verify(boms, never()).updateByPrimaryKey(any());
        verify(items, never()).updateByPrimaryKey(any());
    }

    @Test
    void failedPublicationLeavesDraftAndRejectsZeroOrDisabledMaterial() {
        var root = bom(1, 3, "1");
        root.setStatus("DRAFT");
        fixture(List.of(root), List.of(line(10, 1, 1, "0", null)));
        assertThrows(FoundationException.class, () -> service.publish(1L, 1));
        verify(boms, never()).updateByPrimaryKey(any());
    }

    @Test
    void defaultSwitchIsProductScopedAndRepeatedSelectionHasNoFurtherEffect() {
        var previous = bom(1, 3, "1");
        previous.setDefaultVersion(true);
        var selected = bom(2, 3, "1");
        var otherProduct = bom(3, 4, "1");
        otherProduct.setDefaultVersion(true);
        fixture(List.of(previous, selected, otherProduct), List.of());
        when(boms.selectDefaultByProductId(3L)).thenReturn(previous, selected);
        when(boms.updateByPrimaryKey(any())).thenReturn(1);

        var result = service.setDefault(2L, 1L);

        assertTrue(result.getDefaultVersion());
        assertFalse(previous.getDefaultVersion());
        assertTrue(otherProduct.getDefaultVersion());
        verify(boms, times(2)).updateByPrimaryKey(any());
        assertTrue(service.setDefault(2L, 1L).getDefaultVersion());
        verify(boms, times(2)).updateByPrimaryKey(any());
    }

    @Test
    void draftAndDisabledVersionsCannotBecomeDefaultAndDisableClearsDefault() {
        var draft = bom(1, 3, "1");
        draft.setStatus("DRAFT");
        fixture(List.of(draft), List.of());
        assertThrows(FoundationException.class, () -> service.setDefault(1L, 1L));
        verify(boms, never()).updateByPrimaryKey(any());

        var published = bom(2, 3, "1");
        published.setDefaultVersion(true);
        fixture(List.of(published), List.of());
        when(boms.updateByPrimaryKey(any())).thenReturn(1);
        var disabled = service.disable(2L, 1L);
        assertFalse(disabled.getDefaultVersion());
        assertEquals("DISABLED", disabled.getStatus());
    }

    @Test
    void componentPageAssociatesNamesInBatchesAndPreservesQuantities() {
        fixture(List.of(bom(1, 3, "1")), List.of(line(10, 1, 1, "2.5", null), line(11, 1, 2, "3", null)));
        var result = service.itemPage(new MaterialBomItemQueryDto(0, 20, 1L));
        assertEquals("M1", result.items().getFirst().materialCode());
        assertEquals("物料1", result.items().getFirst().materialName());
        assertEquals("基本单位", result.items().getFirst().unitName());
        assertEquals(new BigDecimal("2.5"), result.items().getFirst().quantity());
        verify(materials, times(1)).selectByExample(any());
        verify(units, times(1)).selectByExample(any());
    }

    @Test
    void everyPublicHttpEntryRetainsExplicitAuthorization() {
        for (var type : List.of(MaterialBomController.class, MaterialBomItemController.class)) {
            for (var method : type.getDeclaredMethods())
                if (Modifier.isPublic(method.getModifiers())) {
                    var access = method.getAnnotation(EndpointAccess.class);
                    assertNotNull(access, method.getName());
                    assertEquals(EndpointAccess.Kind.AUTHORIZED, access.value());
                }
        }
    }

    private void fixture(List<MaterialBomEntity> versions, List<MaterialBomItemEntity> lines) {
        doAnswer(invocation -> {
            MaterialBomExample filter = invocation.getArgument(0);
            var ids = (List<?>) filter.getOredCriteria().getFirst().getAllCriteria().getFirst().getValue();
            return versions.stream().filter(row -> ids.contains(row.getId())).toList();
        }).when(boms).selectByExample(any());
        doAnswer(invocation -> {
            MaterialBomItemExample filter = invocation.getArgument(0);
            var criterion = filter.getOredCriteria().getFirst().getAllCriteria().getFirst();
            if (criterion.getValue() instanceof List<?> ids)
                return lines.stream().filter(row -> ids.contains(row.getBomId())).toList();
            return lines.stream().filter(row -> row.getBomId().equals(criterion.getValue())).toList();
        }).when(items).selectByExample(any());
        for (var row : versions) {
            when(boms.selectByPrimaryKey(row.getId())).thenReturn(row);
            when(boms.selectByPrimaryKeyForUpdate(row.getId())).thenReturn(row);
        }
        var materialRows = List.of(material(1), material(2), material(3), material(4));
        when(materials.selectByExample(any())).thenReturn(materialRows);
        for (var row : materialRows) when(materials.selectByPrimaryKeyForUpdate(row.getId())).thenReturn(row);
        var unit = new UnitMeasureEntity();
        unit.setId(1L);
        unit.setCode("BASE");
        unit.setName("基本单位");
        unit.setStatus("ENABLED");
        when(units.selectByExample(any())).thenReturn(List.of(unit));
        when(units.selectByPrimaryKeyForUpdate(1L)).thenReturn(unit);
    }

    private static MaterialBomEntity bom(long id, long product, String base) {
        var r = new MaterialBomEntity();
        r.setId(id);
        r.setProductId(product);
        r.setBaseQty(new BigDecimal(base));
        r.setUnitId(1L);
        r.setStatus("PUBLISHED");
        r.setRevision(1L);
        return r;
    }

    private static MaterialBomItemEntity line(long id, long bom, long material, String qty, Long child) {
        var r = new MaterialBomItemEntity();
        r.setId(id);
        r.setBomId(bom);
        r.setMaterialId(material);
        r.setQuantity(new BigDecimal(qty));
        r.setUnitId(1L);
        r.setChildBomId(child);
        r.setSort(0);
        return r;
    }

    private static MaterialEntity material(long id) {
        var r = new MaterialEntity();
        r.setId(id);
        r.setCode("M" + id);
        r.setName("物料" + id);
        r.setUnitId(1L);
        r.setStatus("ENABLED");
        return r;
    }
}
