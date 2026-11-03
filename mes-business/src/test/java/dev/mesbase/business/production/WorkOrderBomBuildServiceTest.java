package dev.mesbase.business.production;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.mesbase.business.basic.dao.MaterialBomMapper;
import dev.mesbase.business.basic.dao.MaterialMapper;
import dev.mesbase.business.basic.domain.MaterialBomDirect;
import dev.mesbase.business.basic.domain.MaterialBomEntity;
import dev.mesbase.business.basic.domain.MaterialBomExplosion;
import dev.mesbase.business.basic.domain.MaterialEntity;
import dev.mesbase.business.basic.service.MaterialBomService;
import dev.mesbase.business.production.domain.WorkOrderBomBuildModels;
import dev.mesbase.business.production.service.WorkOrderBomBuildService;
import dev.mesbase.business.production.service.WorkOrderService;
import dev.mesbase.system.service.AutoCodeNumbering;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class WorkOrderBomBuildServiceTest {
    @Test
    void previewBuildsDeepOrdersAndPreservesTheSameProductAtDistinctBomPaths() {
        var bomService = mock(MaterialBomService.class);
        var boms = mock(MaterialBomMapper.class);
        var materials = mock(MaterialMapper.class);
        var workOrders = mock(WorkOrderService.class);
        var autoCodes = mock(AutoCodeNumbering.class);

        var rootBom = bom(1L, 1L, "ROOT-BOM", 7L);
        var childBom = bom(2L, 2L, "CHILD-A-BOM", 9L);
        var otherChildBom = bom(3L, 5L, "CHILD-B-BOM", 11L);
        var sharedSemiBom = bom(4L, 3L, "SHARED-SEMI-BOM", 13L);
        var rootMaterial = material(1L, "P-ROOT", "WO-ROOT");
        var childMaterial = material(2L, "P-CHILD-A", "WO-CHILD-A");
        var otherChildMaterial = material(5L, "P-CHILD-B", "WO-CHILD-B");
        var sharedSemiMaterial = material(3L, "P-SEMI", "WO-SEMI");
        when(materials.selectByPrimaryKey(1L)).thenReturn(rootMaterial);
        when(materials.selectByPrimaryKey(2L)).thenReturn(childMaterial);
        when(materials.selectByPrimaryKey(3L)).thenReturn(sharedSemiMaterial);
        when(materials.selectByPrimaryKey(5L)).thenReturn(otherChildMaterial);
        when(boms.selectByPrimaryKey(2L)).thenReturn(childBom);
        when(boms.selectByPrimaryKey(3L)).thenReturn(otherChildBom);
        when(boms.selectByPrimaryKey(4L)).thenReturn(sharedSemiBom);

        var semiLeafA = leaf("/10/20/30", 3, 30L, 6L, bd("3"), bd("24"));
        var semiLeafB = leaf("/11/21/30", 3, 30L, 6L, bd("3"), bd("18"));
        var sharedSemiA = new MaterialBomExplosion.Node("/10/20", 2, 20L, 2L, "V2", 3L,
                "P-SEMI", "半成品", 1L, "件", bd("2"), bd("1"), bd("8"), 4L, false,
                List.of(semiLeafA));
        var sharedSemiB = new MaterialBomExplosion.Node("/11/21", 2, 21L, 3L, "V3", 3L,
                "P-SEMI", "半成品", 1L, "件", bd("1"), bd("1"), bd("6"), 4L, false,
                List.of(semiLeafB));
        var assemblyA = new MaterialBomExplosion.Node("/10", 1, 10L, 1L, "V1", 2L,
                "P-CHILD-A", "子件A", 1L, "件", bd("2"), bd("1"), bd("4"), 2L, false,
                List.of(sharedSemiA));
        var assemblyB = new MaterialBomExplosion.Node("/11", 1, 11L, 1L, "V1", 5L,
                "P-CHILD-B", "子件B", 1L, "件", bd("3"), bd("1"), bd("6"), 3L, false,
                List.of(sharedSemiB));
        var peer = new MaterialBomExplosion.Node("/12", 1, 12L, 1L, "V1", 8L,
                "RAW-PEER", "同阶原料", 1L, "件", bd("0.5"), bd("1"), bd("1"), null, true, List.of());
        when(bomService.explode("P-ROOT", 1L, bd("2"), 0, 1)).thenReturn(new MaterialBomExplosion(
                rootMaterial, bd("2"), rootBom, new MaterialBomExplosion.Versions(List.of(rootBom), 1, 0, 1),
                false, true, List.of(assemblyA, assemblyB, peer), List.of(), List.of()));
        when(bomService.direct(1L, bd("2"))).thenReturn(new MaterialBomDirect(rootBom, List.of(
                new MaterialBomDirect.Component(10L, 2L, 1L, bd("2"), bd("1"), bd("4")),
                new MaterialBomDirect.Component(11L, 5L, 1L, bd("3"), bd("1"), bd("6")),
                new MaterialBomDirect.Component(12L, 8L, 1L, bd("0.5"), bd("1"), bd("1")))));
        when(bomService.direct(2L, bd("4"))).thenReturn(new MaterialBomDirect(childBom, List.of(
                new MaterialBomDirect.Component(20L, 3L, 1L, bd("2"), bd("1"), bd("8")))));
        when(bomService.direct(3L, bd("6"))).thenReturn(new MaterialBomDirect(otherChildBom, List.of(
                new MaterialBomDirect.Component(21L, 3L, 1L, bd("1"), bd("1"), bd("6")))));
        when(bomService.direct(4L, bd("8"))).thenReturn(new MaterialBomDirect(sharedSemiBom, List.of(
                new MaterialBomDirect.Component(30L, 6L, 1L, bd("3"), bd("1"), bd("24")))));
        when(bomService.direct(4L, bd("6"))).thenReturn(new MaterialBomDirect(sharedSemiBom, List.of(
                new MaterialBomDirect.Component(30L, 6L, 1L, bd("3"), bd("1"), bd("18")))));
        when(workOrders.applicableRoutings(anyLong(), any())).thenReturn(List.of());

        var service = new WorkOrderBomBuildService(bomService, boms, materials, workOrders, autoCodes, mock(org.springframework.transaction.PlatformTransactionManager.class));
        var preview = service.preview(new WorkOrderBomBuildModels.PreviewRequest(1L, 1L, bd("2")));

        assertTrue(preview.complete());
        assertEquals(5, preview.orders().size());
        assertEquals("ROOT", preview.orders().getFirst().path());
        assertEquals(3, preview.orders().getFirst().materials().size());
        assertEquals("/10", preview.orders().get(1).path());
        assertEquals("ROOT", preview.orders().get(1).parentPath());
        assertEquals("4", preview.orders().get(1).plannedQty().toPlainString());
        assertEquals("9", preview.orders().get(1).bomRevision().toString());
        assertEquals(1, preview.orders().get(1).materials().size());
        assertEquals("/10/20", preview.orders().get(2).path());
        assertEquals("/11/21", preview.orders().get(4).path());
        assertEquals(preview.orders().get(2).productId(), preview.orders().get(4).productId());
        assertNotEquals(preview.orders().get(2).path(), preview.orders().get(4).path());
        assertEquals("24", preview.orders().get(2).materials().getFirst().quantity().toPlainString());
        assertEquals("18", preview.orders().get(4).materials().getFirst().quantity().toPlainString());
    }

    @Test
    void confirmRejectsBomRevisionChangeSincePreview() {
        var bomService = mock(MaterialBomService.class);
        var boms = mock(MaterialBomMapper.class);
        var materials = mock(MaterialMapper.class);
        var workOrders = mock(WorkOrderService.class);
        var autoCodes = mock(AutoCodeNumbering.class);
        var rootBom = bom(1L, 1L, "ROOT-BOM", 7L);
        var changedBom = bom(1L, 1L, "ROOT-BOM", 8L);
        var rootMaterial = material(1L, "P-ROOT", "WO-ROOT");
        var line = leaf("/10", 1, 10L, 2L, bd("2"), bd("4"));
        when(materials.selectByPrimaryKey(1L)).thenReturn(rootMaterial);
        when(workOrders.bomBuildReplay(org.mockito.ArgumentMatchers.eq("request-1"),
                org.mockito.ArgumentMatchers.anyString())).thenReturn(null);
        when(workOrders.applicableRoutings(anyLong(), any())).thenReturn(List.of());
        when(bomService.explode("P-ROOT", 1L, bd("2"), 0, 1)).thenReturn(
                new MaterialBomExplosion(rootMaterial, bd("2"), rootBom,
                        new MaterialBomExplosion.Versions(List.of(rootBom), 1, 0, 1), false, true,
                        List.of(line), List.of(), List.of()),
                new MaterialBomExplosion(rootMaterial, bd("2"), changedBom,
                        new MaterialBomExplosion.Versions(List.of(changedBom), 1, 0, 1), false, true,
                        List.of(line), List.of(), List.of()));
        when(bomService.direct(1L, bd("2"))).thenReturn(
                new MaterialBomDirect(rootBom, List.of(new MaterialBomDirect.Component(10L, 2L, 1L,
                        bd("2"), bd("1"), bd("4")))),
                new MaterialBomDirect(changedBom, List.of(new MaterialBomDirect.Component(10L, 2L, 1L,
                        bd("2"), bd("1"), bd("4")))));
        var service = new WorkOrderBomBuildService(bomService, boms, materials, workOrders, autoCodes, mock(org.springframework.transaction.PlatformTransactionManager.class));
        var oldPreview = service.preview(new WorkOrderBomBuildModels.PreviewRequest(1L, 1L, bd("2")));
        var confirm = new WorkOrderBomBuildModels.Confirm("request-1", oldPreview.fingerprint(), List.of(
                new WorkOrderBomBuildModels.OrderChoice("ROOT", "WO-ROOT", null, null, null, "")));

        assertEquals(dev.mesbase.core.error.ErrorCode.PRODUCTION_INPUT_STALE,
                org.junit.jupiter.api.Assertions.assertThrows(dev.mesbase.core.error.FoundationException.class,
                                () -> service.confirm(new WorkOrderBomBuildModels.PreviewRequest(1L, 1L, bd("2")), confirm))
                        .code());
        org.mockito.Mockito.verify(workOrders, org.mockito.Mockito.never()).createBomBuild(any());
    }

    private static MaterialBomEntity bom(Long id, Long productId, String code, Long revision) {
        var row = new MaterialBomEntity();
        row.setId(id);
        row.setProductId(productId);
        row.setCode(code);
        row.setVersion("V" + id);
        row.setRevision(revision);
        row.setStatus("PUBLISHED");
        return row;
    }

    private static MaterialEntity material(Long id, String code, String defaultRule) {
        var row = new MaterialEntity();
        row.setId(id);
        row.setCode(code);
        row.setName(code);
        row.setWorkOrderRuleCode(defaultRule);
        return row;
    }

    private static MaterialBomExplosion.Node leaf(String path, int depth, Long lineId, Long materialId,
                                                  BigDecimal component, BigDecimal quantity) {
        return new MaterialBomExplosion.Node(path, depth, lineId, depth == 1 ? 1L : 4L,
                depth == 1 ? "V1" : "V4", materialId, "M-" + materialId, "物料" + materialId,
                1L, "件", component, bd("1"), quantity, null, true, List.of());
    }

    private static BigDecimal bd(String value) {
        return new BigDecimal(value);
    }
}
