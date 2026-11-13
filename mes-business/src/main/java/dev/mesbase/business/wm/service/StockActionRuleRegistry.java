package dev.mesbase.business.wm.service;

import dev.mesbase.business.production.service.MaterialQuantityRules;
import dev.mesbase.business.wm.dao.WmStockActionMapper;
import dev.mesbase.business.wm.dao.WmStockDocumentLineMapper;
import dev.mesbase.business.wm.dao.WmStockDocumentMapper;
import dev.mesbase.business.wm.domain.StockActionType;
import dev.mesbase.business.wm.domain.StockCommand;
import dev.mesbase.business.wm.domain.StockDocumentDirection;
import dev.mesbase.business.wm.domain.StockDocumentStatus;
import dev.mesbase.business.wm.domain.StockDocumentType;
import dev.mesbase.business.wm.domain.StockSourceType;
import dev.mesbase.business.wm.domain.WmInventoryEntity;
import dev.mesbase.business.wm.domain.WmStockActionEntity;
import dev.mesbase.business.wm.domain.WmStockDocumentEntity;
import dev.mesbase.business.wm.domain.WmStockActionExample;
import dev.mesbase.business.wm.domain.WmStockDocumentLineEntity;
import dev.mesbase.business.wm.domain.WmStockDocumentLineExample;
import dev.mesbase.common.validation.ParameterChecks;
import dev.mesbase.core.error.ErrorCode;
import dev.mesbase.core.error.FoundationException;
import dev.mesbase.core.error.SafeBusinessMessage;
import java.math.BigDecimal;
import java.util.Objects;

/**
 * Action-specific interpretation only. LocalStockService owns locks, balance updates and the ledger.
 * Plans that read balances or return totals must be built after its inventory lock is acquired.
 */
final class StockActionRuleRegistry {
    private final OpeningRule opening;
    private final ReturnRule returns;

    StockActionRuleRegistry(WmStockActionMapper actions, WmStockDocumentLineMapper lines,
                            WmStockDocumentMapper documents) {
        opening = new OpeningRule(actions, lines);
        returns = new ReturnRule(actions, lines, documents);
    }

    Rule manual(StockActionType type) {
        if (type == null) invalidType();
        return switch (type) {
            case OPENING -> opening;
            case GAIN, LOSS, CORRECTION -> new AdjustmentRule(type);
            case RETURN -> returns;
            // Receipts must be posted through a formally approved stock document.
            case RECEIPT -> throw new FoundationException(ErrorCode.STOCK_DOCUMENT_REQUIRED);
            default -> throw new FoundationException(ErrorCode.BAD_REQUEST,
                    new SafeBusinessMessage("库存动作类型无效"));
        };
    }

    private static void invalidType() {
        ParameterChecks.reject(ErrorCode.BAD_REQUEST, new SafeBusinessMessage("库存动作类型无效"));
    }

    interface Rule {
        default void validateParameters(StockCommand command) {
            if (command.documentId() != null || command.documentLineId() != null)
                ParameterChecks.reject(ErrorCode.BAD_REQUEST, new SafeBusinessMessage("仅退料操作可以关联原库存单"));
        }

        default void checkQuantity(StockCommand command, WmInventoryEntity stock, MaterialQuantityRules quantities) {
            quantities.checkForWrite(command.quantity(), stock.getMaterialId());
        }

        Plan plan(StockCommand command, WmInventoryEntity stock);
    }

    record Plan(BigDecimal delta, StockSourceType sourceType, String sourceId, String referenceActionId,
                String executionCode, WmStockDocumentLineEntity originalLine) {
        static Plan inventory(BigDecimal delta, WmInventoryEntity stock) {
            return new Plan(delta, StockSourceType.INVENTORY, stock.getId().toString(), null, null, null);
        }
    }

    private record OpeningRule(WmStockActionMapper actions, WmStockDocumentLineMapper lines) implements Rule {
        @Override
        public Plan plan(StockCommand command, WmInventoryEntity stock) {
            var actionFilter = new WmStockActionExample();
            actionFilter.createCriteria().andStockIdEqualTo(stock.getId());
            var lineFilter = new WmStockDocumentLineExample();
            lineFilter.createCriteria().andStockIdEqualTo(stock.getId());
            BigDecimal available = stock.getAvailableQuantity();
            if (available == null) available = stock.getQuantity();
            if (command.quantity().signum() < 0 || stock.getQuantity().signum() != 0
                    || available.signum() != 0 || actions.countByExample(actionFilter) > 0
                    || lines.countByExample(lineFilter) > 0)
                ParameterChecks.reject(ErrorCode.RESOURCE_CONFLICT);
            return Plan.inventory(command.quantity(), stock);
        }
    }

    private record AdjustmentRule(StockActionType type) implements Rule {
        @Override
        public void checkQuantity(StockCommand command, WmInventoryEntity stock, MaterialQuantityRules quantities) {
            if (type == StockActionType.LOSS)
                quantities.checkSettlementForWrite(command.quantity(), stock.getQuantity(), stock.getMaterialId());
            else if (type == StockActionType.CORRECTION && command.quantity().signum() < 0)
                quantities.checkSettlementForWrite(command.quantity()
                        .negate(), stock.getQuantity(), stock.getMaterialId());
            else quantities.checkForWrite(command.quantity(), stock.getMaterialId());
        }

        @Override
        public Plan plan(StockCommand command, WmInventoryEntity stock) {
            BigDecimal delta = command.quantity();
            if (type == StockActionType.CORRECTION) {
                if (delta.signum() == 0)
                    ParameterChecks.reject(ErrorCode.BAD_REQUEST, new SafeBusinessMessage("库存调整数量不能为零"));
            } else {
                positive(delta);
                if (type == StockActionType.LOSS) delta = delta.negate();
            }
            return Plan.inventory(delta, stock);
        }
    }

    private record ReturnRule(WmStockActionMapper actions, WmStockDocumentLineMapper lines,
                              WmStockDocumentMapper documents) implements Rule {
        @Override
        public void validateParameters(StockCommand command) {
            // Missing document is checked after replay so a frozen request keeps its replay semantics.
        }

        @Override
        public Plan plan(StockCommand command, WmInventoryEntity stock) {
            if (command.documentId() == null)
                ParameterChecks.reject(ErrorCode.BAD_REQUEST, new SafeBusinessMessage("退料操作必须关联原库存单"));
            positive(command.quantity());
            var document = documents.selectByPrimaryKey(command.documentId());
            if (document == null || !StockDocumentStatus.POSTED.getValue().equals(document.getStatus())
                    || document.getSourceExecutionCode() == null)
                ParameterChecks.reject(ErrorCode.RESOURCE_CONFLICT);
            var filter = new WmStockDocumentLineExample();
            filter.createCriteria().andDocumentIdEqualTo(document.getId());
            var rows = lines.selectByExample(filter);
            Long lineId = command.documentLineId();
            if (lineId == null) {
                if (rows.size() != 1) throw new FoundationException(ErrorCode.BAD_REQUEST);
                lineId = rows.getFirst().getId();
            }
            Long selected = lineId;
            var line = rows.stream().filter(row -> selected.equals(row.getId())
                            && command.stockId().equals(row.getStockId()))
                    .findFirst().orElseThrow(() -> new FoundationException(ErrorCode.RESOURCE_CONFLICT));
            if (line.getStockActionId() == null) ParameterChecks.reject(ErrorCode.RESOURCE_CONFLICT);
            var original = actions.selectByPrimaryKey(line.getStockActionId());
            if (original == null || !StockActionType.ISSUE.getValue().equals(original.getActionType())
                    || !Objects.equals(original.getSourceId(), document.getId().toString())
                    || !Objects.equals(original.getDocumentLineId(), line.getId())
                    || !Objects.equals(original.getStockId(), stock.getId())
                    || !Objects.equals(original.getMaterialId(), line.getMaterialId())
                    || !Objects.equals(original.getWarehouseId(), line.getWarehouseId())
                    || !Objects.equals(original.getUnitId(), line.getUnitId())
                    || !Objects.equals(original.getBatchNo(), line.getBatchNo())
                    || !Objects.equals(original.getBinCode(), line.getBinCode())
                    || original.getQuantityDelta() == null || original.getQuantityDelta().signum() >= 0
                    || original.getQuantityDelta().abs().compareTo(line.getQuantity()) != 0
                    || actions.sumReturns(original.getId(), StockActionType.REVERSAL.getValue()).signum() != 0
                    || actions.sumReturns(original.getId(), StockActionType.RETURN.getValue())
                    .add(command.quantity()).compareTo(line.getQuantity()) > 0)
                ParameterChecks.reject(ErrorCode.RESOURCE_CONFLICT);
            return new Plan(command.quantity(), StockSourceType.MATERIAL_ISSUE, document.getId().toString(),
                    original.getId(), document.getSourceExecutionCode(), line);
        }
    }

    record DocumentPostingRule(StockDocumentDirection direction, StockActionType actionType,
                               StockSourceType sourceType) {
        static DocumentPostingRule resolve(String direction, String type) {
            var purpose = StockDocumentType.from(type);
            var flow = StockDocumentDirection.from(direction);
            if (purpose == null || flow == null || purpose.getDirection() != flow || !purpose.isApprovalAvailable())
                ParameterChecks.reject(ErrorCode.STOCK_DOCUMENT_SOURCE_PENDING);
            if (purpose == StockDocumentType.MATERIAL_ISSUE)
                return new DocumentPostingRule(flow, StockActionType.ISSUE, StockSourceType.MATERIAL_ISSUE);
            if (flow == StockDocumentDirection.IN)
                return new DocumentPostingRule(flow, StockActionType.OTHER_RECEIPT, StockSourceType.OTHER_RECEIPT);
            return new DocumentPostingRule(flow, StockActionType.OTHER_ISSUE, StockSourceType.OTHER_ISSUE);
        }

        BigDecimal delta(BigDecimal quantity) {
            return direction == StockDocumentDirection.IN ? quantity : quantity.negate();
        }
    }

    record DocumentReversalPlan(BigDecimal delta, StockSourceType sourceType, String sourceId,
                                String referenceActionId, String executionCode) {
    }

    static final class DocumentReversalRule {
        private DocumentReversalRule() {
        }

        static DocumentReversalPlan plan(WmStockDocumentEntity document, WmStockDocumentLineEntity line,
                                         WmStockActionEntity original,
                                         BigDecimal returned) {
            if (document == null || document.getId() == null || line == null || original == null)
                ParameterChecks.reject(ErrorCode.RESOURCE_CONFLICT);
            var posting = DocumentPostingRule.resolve(document.getDirection(), document.getDocumentType());
            if (line.getQuantity() == null || line.getQuantity().signum() <= 0
                    || !Objects.equals(document.getId(), line.getDocumentId())
                    || !Objects.equals(line.getStockActionId(), original.getId())
                    || !Objects.equals(original.getActionType(), posting.actionType().getValue())
                    || !Objects.equals(original.getSourceType(), posting.sourceType().getValue())
                    || !Objects.equals(original.getSourceId(), document.getId().toString())
                    || !Objects.equals(original.getDocumentLineId(), line.getId())
                    || !Objects.equals(original.getStockId(), line.getStockId())
                    || !Objects.equals(original.getMaterialId(), line.getMaterialId())
                    || !Objects.equals(original.getWarehouseId(), line.getWarehouseId())
                    || !Objects.equals(original.getUnitId(), line.getUnitId())
                    || !Objects.equals(original.getBatchNo(), line.getBatchNo())
                    || !Objects.equals(original.getBinCode(), line.getBinCode())
                    || (document.getSourceExecutionCode() != null
                    && !Objects.equals(original.getExecutionCode(), document.getSourceExecutionCode()))
                    || original.getQuantityDelta() == null
                    || original.getQuantityDelta().compareTo(posting.delta(line.getQuantity())) != 0
                    || returned == null || returned.signum() < 0
                    || (returned.signum() != 0 && posting.actionType() != StockActionType.ISSUE))
                ParameterChecks.reject(ErrorCode.RESOURCE_CONFLICT);

            BigDecimal remaining = original.getQuantityDelta().add(returned);
            if (remaining.signum() != 0
                    && remaining.signum() != original.getQuantityDelta().signum())
                ParameterChecks.reject(ErrorCode.RESOURCE_CONFLICT);
            return new DocumentReversalPlan(remaining.negate(), posting.sourceType(), original.getSourceId(),
                    original.getId(), original.getExecutionCode());
        }
    }

    private static void positive(BigDecimal quantity) {
        if (quantity.signum() <= 0)
            ParameterChecks.reject(ErrorCode.BAD_REQUEST, new SafeBusinessMessage("该库存动作的数量必须大于零"));
    }
}
