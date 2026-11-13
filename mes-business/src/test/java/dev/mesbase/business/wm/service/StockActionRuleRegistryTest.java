package dev.mesbase.business.wm.service;

import dev.mesbase.business.basic.dao.MaterialMapper;
import dev.mesbase.business.basic.dao.UnitMeasureMapper;
import dev.mesbase.business.integration.warehouse.WarehouseIntegration;
import dev.mesbase.business.production.service.BusinessSourceRules;
import dev.mesbase.business.production.service.MaterialQuantityRules;
import dev.mesbase.business.wm.dao.WmInventoryMapper;
import dev.mesbase.business.wm.dao.WmStockActionMapper;
import dev.mesbase.business.wm.dao.WmStockDocumentLineMapper;
import dev.mesbase.business.wm.dao.WmStockDocumentMapper;
import dev.mesbase.business.wm.dao.WmWarehouseMapper;
import dev.mesbase.business.wm.domain.StockActionType;
import dev.mesbase.business.wm.domain.StockCommand;
import dev.mesbase.business.wm.domain.StockDocumentDirection;
import dev.mesbase.business.wm.domain.StockSourceType;
import dev.mesbase.business.wm.domain.WmInventoryEntity;
import dev.mesbase.business.wm.domain.WmStockActionEntity;
import dev.mesbase.business.wm.domain.WmStockDocumentEntity;
import dev.mesbase.business.wm.domain.WmStockDocumentLineEntity;
import dev.mesbase.common.utils.SecurityUtils;
import dev.mesbase.core.error.FoundationException;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * TEST ONLY: isolated action interpretation; transaction and ledger regressions live in H2 HTTP tests.
 */
class StockActionRuleRegistryTest {
    private final WmStockActionMapper actions = mock(WmStockActionMapper.class);
    private final WmStockDocumentLineMapper lines = mock(WmStockDocumentLineMapper.class);
    private final WmStockDocumentMapper documents = mock(WmStockDocumentMapper.class);
    private final StockActionRuleRegistry registry = new StockActionRuleRegistry(actions, lines, documents);

    @Test
    void openingRequiresBothZeroBalancesAndNoPriorReferences() {
        var stock = stock("0");
        assertEquals(BigDecimal.TEN, registry.manual(StockActionType.OPENING).plan(command("10"), stock).delta());
        stock.setAvailableQuantity(BigDecimal.ONE);
        assertThrows(FoundationException.class, () -> registry.manual(StockActionType.OPENING)
                .plan(command("10"), stock));
        stock.setAvailableQuantity(BigDecimal.ZERO);
        when(lines.countByExample(any())).thenReturn(1L);
        assertThrows(FoundationException.class, () -> registry.manual(StockActionType.OPENING)
                .plan(command("10"), stock));
    }

    @Test
    void openingRejectsNegativeAndNonzeroExistingStock() {
        assertThrows(FoundationException.class, () -> registry.manual(StockActionType.OPENING)
                .plan(command("-1"), stock("0")));
        assertThrows(FoundationException.class, () -> registry.manual(StockActionType.OPENING)
                .plan(command("1"), stock("1")));
        when(actions.countByExample(any())).thenReturn(1L);
        assertThrows(FoundationException.class, () -> registry.manual(StockActionType.OPENING)
                .plan(command("1"), stock("0")));
    }

    @Test
    void adjustmentsHaveExplicitSignedPlansWithoutWritingBalances() {
        var stock = stock("10");
        assertEquals(BigDecimal.ONE, registry.manual(StockActionType.GAIN).plan(command("1"), stock).delta());
        assertEquals(BigDecimal.ONE.negate(), registry.manual(StockActionType.LOSS).plan(command("1"), stock).delta());
        assertEquals(BigDecimal.ONE, registry.manual(StockActionType.CORRECTION).plan(command("1"), stock).delta());
        assertEquals(BigDecimal.ONE.negate(), registry.manual(StockActionType.CORRECTION).plan(command("-1"), stock)
                .delta());
        assertEquals(BigDecimal.TEN, stock.getQuantity());
        assertThrows(FoundationException.class, () -> registry.manual(StockActionType.LOSS).plan(command("0"), stock));
        assertThrows(FoundationException.class, () -> registry.manual(StockActionType.GAIN).plan(command("-1"), stock));
        assertThrows(FoundationException.class, () -> registry.manual(StockActionType.CORRECTION)
                .plan(command("0"), stock));
    }

    @Test
    void negativeAdjustmentsKeepSettlementPrecisionAndPositiveOnesUseInputPrecision() {
        var quantities = mock(MaterialQuantityRules.class);
        var stock = stock("10");
        registry.manual(StockActionType.LOSS).checkQuantity(command("10"), stock, quantities);
        registry.manual(StockActionType.CORRECTION).checkQuantity(command("-1"), stock, quantities);
        registry.manual(StockActionType.GAIN).checkQuantity(command("2"), stock, quantities);
        verify(quantities).checkSettlementForWrite(BigDecimal.TEN, BigDecimal.TEN, 2L);
        verify(quantities).checkSettlementForWrite(BigDecimal.ONE, BigDecimal.TEN, 2L);
        verify(quantities).checkForWrite(new BigDecimal("2"), 2L);
    }

    @Test
    void manualEntryRejectsPostingAndReversalTypesAndNonreturnDocumentParameters() {
        for (var type : List.of(StockActionType.RECEIPT, StockActionType.ISSUE, StockActionType.OTHER_RECEIPT,
                StockActionType.PRODUCT_RECEIPT, StockActionType.REVERSAL))
            assertThrows(FoundationException.class, () -> registry.manual(type));
        assertThrows(FoundationException.class, () -> registry.manual(null));
        var lineOnly = new StockCommand(UUID.randomUUID().toString(), 1L, BigDecimal.ONE, BigDecimal.TEN,
                BigDecimal.TEN, "TEST ONLY invalid parameter", null, 5L);
        assertThrows(FoundationException.class, () -> registry.manual(StockActionType.GAIN)
                .validateParameters(lineOnly));
        assertThrows(FoundationException.class, () -> registry.manual(StockActionType.GAIN)
                .validateParameters(returnCommand("1")));
    }

    @Test
    void returnReferencesTheIssueAndCountsPriorReturnsOnce() {
        issue();
        var plan = registry.manual(StockActionType.RETURN).plan(returnCommand("4"), stock("10"));
        assertEquals("issue", plan.referenceActionId());
        assertEquals(StockSourceType.MATERIAL_ISSUE, plan.sourceType());
        assertEquals("3", plan.sourceId());
        assertEquals("EX", plan.executionCode());
        assertEquals(5L, plan.originalLine().getId());
        when(actions.sumReturns("issue", "RETURN")).thenReturn(new BigDecimal("7"));
        assertThrows(FoundationException.class, () -> registry.manual(StockActionType.RETURN)
                .plan(returnCommand("4"), stock("10")));
    }

    @Test
    void returnRejectsReversedSourceAndWrongOriginalLine() {
        issue();
        when(actions.sumReturns("issue", "REVERSAL")).thenReturn(BigDecimal.ONE);
        assertThrows(FoundationException.class, () -> registry.manual(StockActionType.RETURN)
                .plan(returnCommand("1"), stock("10")));
        when(actions.sumReturns("issue", "REVERSAL")).thenReturn(BigDecimal.ZERO);
        documents.selectByPrimaryKey(3L).setStatus("REVERSED");
        assertThrows(FoundationException.class, () -> registry.manual(StockActionType.RETURN)
                .plan(returnCommand("1"), stock("10")));
        documents.selectByPrimaryKey(3L).setStatus("POSTED");
        actions.selectByPrimaryKey("issue").setDocumentLineId(999L);
        assertThrows(FoundationException.class, () -> registry.manual(StockActionType.RETURN)
                .plan(returnCommand("1"), stock("10")));
    }

    @Test
    void returnRejectsMismatchedOriginalInventoryAndQuantity() {
        issue();
        var original = actions.selectByPrimaryKey("issue");
        original.setStockId(999L);
        assertThrows(FoundationException.class, () -> registry.manual(StockActionType.RETURN)
                .plan(returnCommand("1"), stock("10")));
        original.setStockId(1L);
        original.setMaterialId(999L);
        assertThrows(FoundationException.class, () -> registry.manual(StockActionType.RETURN)
                .plan(returnCommand("1"), stock("10")));
        original.setMaterialId(2L);
        original.setQuantityDelta(new BigDecimal("-9"));
        assertThrows(FoundationException.class, () -> registry.manual(StockActionType.RETURN)
                .plan(returnCommand("1"), stock("10")));
    }

    @Test
    void multiLineReturnsRequireAnExplicitOriginalLine() {
        issue();
        var another = new WmStockDocumentLineEntity();
        another.setId(6L);
        another.setStockId(7L);
        var first = lines.selectByExample(null).getFirst();
        when(lines.selectByExample(any())).thenReturn(List.of(first, another));
        assertThrows(FoundationException.class, () -> registry.manual(StockActionType.RETURN)
                .plan(returnCommand("1"), stock("10")));
        var command = new StockCommand(UUID.randomUUID().toString(), 1L, BigDecimal.ONE, BigDecimal.TEN,
                BigDecimal.TEN, "TEST ONLY return", 3L, 5L);
        assertEquals("issue", registry.manual(StockActionType.RETURN).plan(command, stock("10")).referenceActionId());
    }

    @Test
    void returnTotalsAndSourceAreReadOnlyAfterTheInventoryLock() {
        issue();
        when(actions.sumReturns("issue", "RETURN")).thenReturn(BigDecimal.TEN);
        var inventory = mock(WmInventoryMapper.class);
        var quantities = mock(MaterialQuantityRules.class);
        var stock = stock("10");
        when(inventory.selectByPrimaryKey(1L)).thenReturn(stock);
        when(inventory.selectByPrimaryKeyForUpdate(1L)).thenReturn(stock);
        var service = new LocalStockService(inventory, actions, mock(MaterialMapper.class),
                mock(UnitMeasureMapper.class), mock(WmWarehouseMapper.class), lines, documents,
                mock(WarehouseIntegration.class), mock(BusinessSourceRules.class), quantities);
        try (var security = mockStatic(SecurityUtils.class)) {
            security.when(SecurityUtils::getUserId).thenReturn(100L);
            assertThrows(FoundationException.class, () -> service.execute(StockActionType.RETURN, returnCommand("1")));
        }
        var ordered = inOrder(inventory, quantities, documents, actions);
        ordered.verify(actions).selectByPrimaryKey(any());
        ordered.verify(inventory).selectByPrimaryKey(1L);
        ordered.verify(quantities).lockForWrite(List.of(2L));
        ordered.verify(inventory).selectByPrimaryKey(1L);
        ordered.verify(inventory).selectByPrimaryKeyForUpdate(1L);
        ordered.verify(actions).selectByPrimaryKey(any());
        ordered.verify(quantities).checkForWrite(BigDecimal.ONE, 2L);
        ordered.verify(documents).selectByPrimaryKey(3L);
        ordered.verify(actions).selectByPrimaryKey("issue");
        ordered.verify(actions).sumReturns("issue", "REVERSAL");
        ordered.verify(actions).sumReturns("issue", "RETURN");
        verify(inventory, never()).updateByPrimaryKey(any());
        verify(actions, never()).insert(any());
    }

    @Test
    void documentPostingRulesKeepPurposeDirectionAndSignedQuantity() {
        var issue = StockActionRuleRegistry.DocumentPostingRule.resolve("OUT", "MATERIAL_ISSUE");
        assertEquals(StockActionType.ISSUE, issue.actionType());
        assertEquals(BigDecimal.ONE.negate(), issue.delta(BigDecimal.ONE));
        var receipt = StockActionRuleRegistry.DocumentPostingRule.resolve("IN", "PURCHASE_RECEIPT");
        assertEquals(StockActionType.OTHER_RECEIPT, receipt.actionType());
        assertEquals(BigDecimal.ONE, receipt.delta(BigDecimal.ONE));
        assertThrows(FoundationException.class, () -> StockActionRuleRegistry.DocumentPostingRule.resolve("IN", "MATERIAL_ISSUE"));
        assertThrows(FoundationException.class, () -> StockActionRuleRegistry.DocumentPostingRule.resolve("IN", "REWORK_RECEIPT"));
    }

    @Test
    void documentReversalPlanValidatesOriginalFactsAndOnlyReversesTheUnreturnedAmount() {
        var document = new WmStockDocumentEntity();
        document.setId(3L);
        document.setDirection(StockDocumentDirection.OUT.getValue());
        document.setDocumentType("MATERIAL_ISSUE");
        document.setSourceExecutionCode("EX");
        var line = new WmStockDocumentLineEntity();
        line.setId(5L);
        line.setDocumentId(3L);
        line.setStockId(1L);
        line.setMaterialId(2L);
        line.setWarehouseId(3L);
        line.setUnitId(4L);
        line.setBatchNo("B1");
        line.setBinCode("A-1");
        line.setQuantity(BigDecimal.TEN);
        line.setStockActionId("issue");
        var original = new WmStockActionEntity();
        original.setId("issue");
        original.setStockId(1L);
        original.setMaterialId(2L);
        original.setWarehouseId(3L);
        original.setUnitId(4L);
        original.setBatchNo("B1");
        original.setBinCode("A-1");
        original.setActionType(StockActionType.ISSUE.getValue());
        original.setQuantityDelta(BigDecimal.TEN.negate());
        original.setSourceType(StockSourceType.MATERIAL_ISSUE.getValue());
        original.setSourceId("3");
        original.setDocumentLineId(5L);
        original.setExecutionCode("EX");

        var plan = StockActionRuleRegistry.DocumentReversalRule.plan(document, line, original,
                new BigDecimal("3"));
        assertEquals(new BigDecimal("7"), plan.delta());
        assertEquals(StockSourceType.MATERIAL_ISSUE, plan.sourceType());
        assertEquals("3", plan.sourceId());
        assertEquals("issue", plan.referenceActionId());
        assertEquals("EX", plan.executionCode());

        original.setWarehouseId(99L);
        assertThrows(FoundationException.class, () -> StockActionRuleRegistry.DocumentReversalRule.plan(
                document, line, original, new BigDecimal("3")));
        original.setWarehouseId(3L);
        original.setActionType(StockActionType.OTHER_ISSUE.getValue());
        assertThrows(FoundationException.class, () -> StockActionRuleRegistry.DocumentReversalRule.plan(
                document, line, original, new BigDecimal("3")));
        original.setActionType(StockActionType.ISSUE.getValue());
        assertThrows(FoundationException.class, () -> StockActionRuleRegistry.DocumentReversalRule.plan(
                document, line, original, new BigDecimal("11")));
    }

    private void issue() {
        var document = new WmStockDocumentEntity();
        document.setId(3L);
        document.setStatus("POSTED");
        document.setSourceExecutionCode("EX");
        var line = new WmStockDocumentLineEntity();
        line.setId(5L);
        line.setDocumentId(3L);
        line.setStockId(1L);
        line.setStockActionId("issue");
        line.setQuantity(BigDecimal.TEN);
        var action = new WmStockActionEntity();
        action.setId("issue");
        action.setStockId(1L);
        action.setMaterialId(2L);
        line.setMaterialId(2L);
        action.setActionType("ISSUE");
        action.setSourceId("3");
        action.setDocumentLineId(5L);
        action.setQuantityDelta(BigDecimal.TEN.negate());
        when(documents.selectByPrimaryKey(3L)).thenReturn(document);
        when(lines.selectByExample(any())).thenReturn(List.of(line));
        when(actions.selectByPrimaryKey("issue")).thenReturn(action);
        when(actions.sumReturns("issue", "RETURN")).thenReturn(new BigDecimal("6"));
        when(actions.sumReturns("issue", "REVERSAL")).thenReturn(BigDecimal.ZERO);
    }

    private StockCommand command(String quantity) {
        return new StockCommand(UUID.randomUUID().toString(), 1L, new BigDecimal(quantity),
                BigDecimal.TEN, BigDecimal.TEN, "TEST ONLY rule");
    }

    private StockCommand returnCommand(String quantity) {
        return new StockCommand(UUID.randomUUID().toString(), 1L, new BigDecimal(quantity),
                BigDecimal.TEN, BigDecimal.TEN, "TEST ONLY return", 3L);
    }

    private WmInventoryEntity stock(String quantity) {
        var stock = new WmInventoryEntity();
        stock.setId(1L);
        stock.setMaterialId(2L);
        stock.setQuantity(new BigDecimal(quantity));
        stock.setAvailableQuantity(new BigDecimal(quantity));
        return stock;
    }
}
