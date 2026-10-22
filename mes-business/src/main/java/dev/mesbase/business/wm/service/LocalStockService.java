package dev.mesbase.business.wm.service;

import dev.mesbase.common.utils.StringUtils;
import dev.mesbase.common.validation.ParameterChecks;
import java.util.Optional;

import dev.mesbase.business.production.service.BusinessSourceRules;
import dev.mesbase.business.shared.enums.BusinessSource;

import dev.mesbase.business.wm.dao.WmInventoryMapper;
import dev.mesbase.business.wm.dao.WmStockActionMapper;
import dev.mesbase.business.wm.dao.WmWarehouseMapper;
import dev.mesbase.business.wm.domain.WmInventoryEntity;
import dev.mesbase.business.wm.domain.WmStockDocumentLineEntity;
import dev.mesbase.business.wm.domain.WmStockActionEntity;
import dev.mesbase.business.wm.domain.WmStockActionExample;
import dev.mesbase.business.wm.domain.WmInventoryResource;
import dev.mesbase.business.wm.domain.StockActionType;
import dev.mesbase.business.wm.domain.StockSourceType;
import dev.mesbase.business.wm.domain.StockCommand;
import dev.mesbase.business.basic.dao.MaterialMapper;
import dev.mesbase.business.basic.dao.UnitMeasureMapper;
import dev.mesbase.business.issue.dao.MesMaterialIssueMapper;
import dev.mesbase.business.issue.dao.MesMaterialIssueInputMapper;
import dev.mesbase.business.issue.domain.MesMaterialIssueInputEntity;
import dev.mesbase.business.issue.domain.MesMaterialIssueInputExample;
import dev.mesbase.business.issue.domain.MaterialIssueStatus;
import dev.mesbase.business.integration.production.ProductionSupply.Calculation;
import dev.mesbase.business.integration.warehouse.WarehouseIntegration;
import dev.mesbase.business.integration.warehouse.ErpActionResult.Mode;
import dev.mesbase.business.production.service.ProductionChainLocks;
import dev.mesbase.business.production.dao.MesProductionReportMapper;
import dev.mesbase.business.production.domain.MesProductionReportExample;
import dev.mesbase.business.quality.dao.MesProductionDefectMapper;
import dev.mesbase.business.quality.domain.MesProductionDefectExample;
import dev.mesbase.business.production.dao.MesProductionReceiptMapper;
import dev.mesbase.business.production.domain.MesProductionReceiptExample;
import dev.mesbase.business.production.domain.ProductionScanCode;
import dev.mesbase.business.shared.enums.EnableStatus;
import dev.mesbase.common.utils.SecurityUtils;
import dev.mesbase.core.error.ErrorCode;
import dev.mesbase.core.error.FoundationException;
import dev.mesbase.infrastructure.cache.annotation.BusinessCacheEvict;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import tools.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Isolation;

/**
 * LOCAL余额唯一业务写入者。锁序：工单→执行→领料→库存；动作和调用方事实同事务。
 */
@Service
@ConditionalOnProperty(name = "mes.business.enabled", havingValue = "true")
public class LocalStockService {
    private final BusinessSourceRules sources;
    private final WmInventoryMapper inventory;
    private final WmStockActionMapper actions;
    private final MaterialMapper materials;
    private final UnitMeasureMapper units;
    private final WmWarehouseMapper warehouses;
    private final MesMaterialIssueInputMapper inputs;
    private final MesMaterialIssueMapper issues;
    private final WarehouseIntegration integration;
    private final ProductionChainLocks locks;
    private final MesProductionReportMapper reports;
    private final MesProductionDefectMapper defects;
    private final MesProductionReceiptMapper receipts;
    private final ObjectMapper json = new ObjectMapper();

    public LocalStockService(WmInventoryMapper inventory, WmStockActionMapper actions, MaterialMapper materials,
                             UnitMeasureMapper units, WmWarehouseMapper warehouses, MesMaterialIssueInputMapper inputs,
                             MesMaterialIssueMapper issues, WarehouseIntegration integration,
                             ProductionChainLocks locks, MesProductionReportMapper reports,
                             MesProductionDefectMapper defects, MesProductionReceiptMapper receipts,
                             BusinessSourceRules sources) {
        this.sources = sources;
        this.reports = reports;
        this.defects = defects;
        this.receipts = receipts;
        this.inventory = inventory;
        this.actions = actions;
        this.materials = materials;
        this.units = units;
        this.warehouses = warehouses;
        this.inputs = inputs;
        this.issues = issues;
        this.integration = integration;
        this.locks = locks;
    }

    @Transactional(transactionManager = "businessTransactionManager", isolation = Isolation.READ_COMMITTED, rollbackFor = Exception.class)
    @BusinessCacheEvict(business = WmInventoryResource.PREFIX, scope = BusinessCacheEvict.Scope.LIST_AND_DETAIL)
    public WmStockActionEntity execute(StockActionType type, StockCommand c) {
        if (type == null || !List.of(StockActionType.OPENING, StockActionType.RECEIPT, StockActionType.GAIN, StockActionType.LOSS, StockActionType.CORRECTION, StockActionType.RETURN)
                .contains(type)) ParameterChecks.reject(ErrorCode.BAD_REQUEST);
        String id = uuid(c.requestId());
        decimal(c.quantity());
        decimal(c.expectedQuantity());
        decimal(c.expectedAvailable());
        if (StringUtils.isBlank(c.reason()) || c.reason().length() > 500 || c.stockId() == null) ParameterChecks.reject(ErrorCode.BAD_REQUEST);
        if (type != StockActionType.RETURN && c.issueId() != null) ParameterChecks.reject(ErrorCode.BAD_REQUEST);
        Long actor = SecurityUtils.getUserId();
        String hash = digest(type, c.stockId(), normalized(c.quantity()), normalized(c.expectedQuantity()),
                normalized(c.expectedAvailable()), c.reason(), c.issueId(), actor);
        MesMaterialIssueInputEntity input = null;
        if (type == StockActionType.RETURN) {
            if (c.issueId() == null) ParameterChecks.reject(ErrorCode.BAD_REQUEST);
            locks.issues(List.of(c.issueId()));
            var issue = issues.selectByPrimaryKey(c.issueId());
            if (!MaterialIssueStatus.CONFIRMED.getValue().equals(issue.getStatus())) ParameterChecks.reject(ErrorCode.RESOURCE_CONFLICT);
            var f = new MesMaterialIssueInputExample();
            f.createCriteria().andIssueIdEqualTo(c.issueId());
            var found = inputs.selectByExampleWithBLOBs(f);
            if (found.size() != 1) ParameterChecks.reject(ErrorCode.RESOURCE_CONFLICT);
            input = found.getFirst();
            if (!Boolean.TRUE.equals(input.getStockApplied()) || input.getConfirmedAt() == null
                    || !Mode.LOCAL_ONLY.getValue().equals(input.getSourceMode()) || !c.stockId()
                    .equals(input.getStockId())) ParameterChecks.reject(ErrorCode.RESOURCE_CONFLICT);
        }
        var stock = lock(c.stockId());
        var old = replay(id, hash);
        if (old != null) return old;
        if (type == StockActionType.RECEIPT) throw new FoundationException(ErrorCode.STOCK_DOCUMENT_REQUIRED);
        if (input == null) sources.warehouse(BusinessSource.LOCAL.getValue(), stock.getWarehouseId());
        else {
            var originalExecution = locks.execution(input.getExecutionId());
            sources.frozenLocal(originalExecution.getWorkorderId(), stock.getWarehouseId());
        }
        if (stock.getQuantity()
                .compareTo(c.expectedQuantity()) != 0 || available(stock).compareTo(c.expectedAvailable()) != 0)
            ParameterChecks.reject(ErrorCode.RESOURCE_CONFLICT);
        BigDecimal delta = c.quantity();
        if (type == StockActionType.OPENING) {
            if (delta.signum() < 0 || stock.getQuantity()
                    .signum() != 0 || available(stock).signum() != 0 || referenced(stock.getId())) ParameterChecks.reject(ErrorCode.RESOURCE_CONFLICT);
        } else if (type == StockActionType.CORRECTION) {
            if (delta.signum() == 0) ParameterChecks.reject(ErrorCode.BAD_REQUEST);
        } else {
            if (delta.signum() <= 0) ParameterChecks.reject(ErrorCode.BAD_REQUEST);
            if (type == StockActionType.LOSS) delta = delta.negate();
        }
        String reference = null;
        if (input != null) {
            // No physical consumption attribution exists after production starts. Keep product input unchanged.
            var execution = locks.execution(input.getExecutionId());
            sources.frozenLocal(execution.getWorkorderId(), stock.getWarehouseId());
            var rf = new MesProductionReportExample();
            rf.createCriteria().andExecutionIdEqualTo(input.getExecutionId());
            var df = new MesProductionDefectExample();
            df.createCriteria().andExecutionIdEqualTo(input.getExecutionId());
            var pf = new MesProductionReceiptExample();
            pf.createCriteria().andExecutionIdEqualTo(input.getExecutionId());
            if (execution.getStartTime() != null || reports.countByExample(rf) != 0
                    || defects.countByExample(df) != 0 || receipts.countByExample(pf) != 0) ParameterChecks.reject(ErrorCode.RESOURCE_CONFLICT);
            var original = actions.selectByPrimaryKey(input.getId());
            if (original == null || !StockActionType.ISSUE.getValue().equals(original.getActionType())) ParameterChecks.reject(ErrorCode.RESOURCE_CONFLICT);
            var calculation = json.readValue(input.getCalculationJson(), Calculation.class);
            identity(stock, calculation);
            if (returned(original.getId()).add(delta).compareTo(calculation.issueQty()) > 0) ParameterChecks.reject(ErrorCode.RESOURCE_CONFLICT);
            reference = original.getId();
        }
        return change(stock, id, hash, type, delta, input == null ? StockSourceType.INVENTORY : StockSourceType.MATERIAL_ISSUE,
                input == null ? stock.getId().toString() : input.getIssueId().toString(), reference,
                Optional.ofNullable(input).map(nullableValue -> nullableValue.getExecutionId()).orElse(null), actor, c.reason());
    }

    /**
     * Serialize the first Input reference with zero-source deletion; does not apply stock.
     */
    @Transactional(transactionManager = "businessTransactionManager", propagation = Propagation.MANDATORY)
    public void validateInputSource(MesMaterialIssueInputEntity input, Calculation calculation) {
        requireLocal(input, calculation);
        locks.order(calculation.order().workorderId());
        var sourceOrder = sources.order(calculation.order().workorderId());
        sources.warehouse(sourceOrder.getSource(), calculation.stock().warehouseId());
        var stock = lock(input.getStockId());
        identity(stock, calculation);
        if (!EnableStatus.ENABLED.getValue().equals(stock.getStatus())
                || stock.getQuantity().compareTo(calculation.issueQty()) < 0
                || available(stock).compareTo(calculation.issueQty()) < 0) ParameterChecks.reject(ErrorCode.RESOURCE_CONFLICT);
    }

    /**
     * Only called from the existing confirmation transaction, after the production chain locks.
     */
    @Transactional(transactionManager = "businessTransactionManager", propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    @BusinessCacheEvict(business = WmInventoryResource.PREFIX, scope = BusinessCacheEvict.Scope.LIST_AND_DETAIL)
    public void issue(MesMaterialIssueInputEntity input, Calculation calculation) {
        requireLocal(input, calculation);
        sources.frozenLocal(calculation.order().workorderId(), calculation.stock().warehouseId());
        var stock = lock(input.getStockId());
        identity(stock, calculation);
        String hash = digest(StockActionType.ISSUE, input.getId(), input.getRequestHash());
        if (replay(input.getId(), hash) != null) return;
        decimal(calculation.issueQty());
        if (calculation.issueQty().signum() <= 0) ParameterChecks.reject(ErrorCode.BAD_REQUEST);
        change(stock, input.getId(), hash, StockActionType.ISSUE, calculation.issueQty().negate(),
                StockSourceType.MATERIAL_ISSUE, input.getIssueId().toString(), null, input.getExecutionId(),
                SecurityUtils.getUserId(), "正式生产领料");
    }

    /**
     * Legacy stock_applied remains authoritative; no synthetic historical ISSUE is created.
     */
    @Transactional(transactionManager = "businessTransactionManager", propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    @BusinessCacheEvict(business = WmInventoryResource.PREFIX, scope = BusinessCacheEvict.Scope.LIST_AND_DETAIL)
    public void reverse(MesMaterialIssueInputEntity input, Calculation calculation, String operationId) {
        requireLocal(input, calculation);
        sources.frozenLocal(calculation.order().workorderId(), calculation.stock().warehouseId());
        var stock = lock(input.getStockId());
        identity(stock, calculation);
        String id = uuid(operationId), hash = digest(StockActionType.REVERSAL, input.getId(), operationId);
        if (replay(id, hash) != null) return;
        var original = actions.selectByPrimaryKey(input.getId());
        BigDecimal delta = calculation.issueQty().subtract(returned(input.getId()));
        if (delta.signum() < 0) ParameterChecks.reject(ErrorCode.RESOURCE_CONFLICT);
        change(stock, id, hash, StockActionType.REVERSAL, delta, StockSourceType.MATERIAL_ISSUE,
                input.getIssueId().toString(), Optional.ofNullable(original).map(nullableValue -> nullableValue.getId()).orElse(null), input.getExecutionId(),
                SecurityUtils.getUserId(), original == null ? "历史领料撤销，按原冻结投入恢复余额" : "原领料整单撤销，扣除已退材料后恢复");
    }

    /**
     * Caller holds WorkOrder -> Execution before entering this owner; only the owner locks/writes stock.
     */
    /**
     * The document transaction owns all lines; this owner alone mutates balances and appends facts.
     */
    @Transactional(transactionManager = "businessTransactionManager", propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    @BusinessCacheEvict(business = WmInventoryResource.PREFIX, scope = BusinessCacheEvict.Scope.LIST_AND_DETAIL)
    public WmStockActionEntity postDocument(String type, Long documentId, WmStockDocumentLineEntity line,
                                            String operationId, String actionId, String requestHash, String reason,
                                            Long executionId) {
        var actionType = StockActionType.from(type);
        var sourceType = StockSourceType.from(type);
        if (actionType != StockActionType.OTHER_RECEIPT && actionType != StockActionType.OTHER_ISSUE) ParameterChecks.reject(ErrorCode.BAD_REQUEST);
        uuid(operationId);
        uuid(actionId);
        decimal(line.getQuantity());
        if (line.getQuantity().signum() <= 0 || !Objects.equals(documentId, line.getDocumentId())) ParameterChecks.reject(ErrorCode.BAD_REQUEST);
        var stock = lock(line.getStockId());
        sources.warehouse(BusinessSource.LOCAL.getValue(), stock.getWarehouseId());
        var material = materials.selectByPrimaryKey(stock.getMaterialId());
        if (!Objects.equals(stock.getMaterialId(), line.getMaterialId())
                || !Objects.equals(stock.getWarehouseId(), line.getWarehouseId())
                || !Objects.equals(stock.getBatchNo(), line.getBatchNo())
                || !Objects.equals(stock.getBinCode(), line.getBinCode())
                || material == null || !Objects.equals(material.getUnitId(), line.getUnitId())) ParameterChecks.reject(ErrorCode.RESOURCE_CONFLICT);
        if (actionType == StockActionType.OTHER_ISSUE && (stock.getQuantity().compareTo(line.getQuantity()) < 0
                || available(stock).compareTo(line.getQuantity()) < 0))
            throw new FoundationException(ErrorCode.STOCK_INSUFFICIENT);
        BigDecimal delta = actionType == StockActionType.OTHER_ISSUE ? line.getQuantity().negate() : line.getQuantity();
        return change(stock, actionId, requestHash, actionType, delta, sourceType, documentId.toString(),
                null, executionId, SecurityUtils.getUserId(), reason, line.getId(), operationId);
    }

    @Transactional(transactionManager = "businessTransactionManager", propagation = Propagation.MANDATORY)
    public WmInventoryEntity lock(Long id) {
        if (id == null) ParameterChecks.reject(ErrorCode.RESOURCE_CONFLICT);
        var stock = inventory.selectByPrimaryKeyForUpdate(id);
        if (stock == null) ParameterChecks.reject(ErrorCode.RESOURCE_CONFLICT);
        validateBalances(stock.getQuantity(), available(stock));
        return stock;
    }

    public boolean referenced(Long stockId) {
        var f = new WmStockActionExample();
        f.createCriteria().andStockIdEqualTo(stockId);
        var i = new MesMaterialIssueInputExample();
        i.createCriteria().andStockIdEqualTo(stockId);
        return actions.countByExample(f) > 0 || inputs.countByExample(i) > 0;
    }

    private WmStockActionEntity change(WmInventoryEntity stock, String id, String hash, StockActionType type,
                                       BigDecimal delta, StockSourceType sourceType, String sourceId, String reference,
                                       Long execution,
                                       Long actor, String reason) {
        return change(stock, id, hash, type, delta, sourceType, sourceId, reference, execution, actor, reason,
                null, null);
    }

    private WmStockActionEntity change(WmInventoryEntity stock, String id, String hash, StockActionType type,
                                       BigDecimal delta, StockSourceType sourceType, String sourceId, String reference,
                                       Long execution, Long actor, String reason, Long documentLineId,
                                       String operationId) {
        if (actor == null) ParameterChecks.reject(ErrorCode.RESOURCE_CONFLICT);
        var material = materials.selectByPrimaryKey(stock.getMaterialId());
        var warehouse = warehouses.selectByPrimaryKey(stock.getWarehouseId());
        if (material == null || material.getUnitId() == null) ParameterChecks.reject(ErrorCode.RESOURCE_CONFLICT);
        var unit = units.selectByPrimaryKey(material.getUnitId());
        if (material == null || warehouse == null || unit == null || !EnableStatus.ENABLED.getValue()
                .equals(stock.getStatus())
                || !EnableStatus.ENABLED.getValue().equals(material.getStatus()) || !EnableStatus.ENABLED.getValue()
                .equals(warehouse.getStatus())
                || !EnableStatus.ENABLED.getValue().equals(unit.getStatus())) ParameterChecks.reject(ErrorCode.RESOURCE_CONFLICT);
        BigDecimal before = stock.getQuantity(), avail = available(stock);
        BigDecimal after = before.add(delta), availableAfter = avail.add(delta);
        validateBalances(after, availableAfter);
        var a = new WmStockActionEntity();
        a.setId(id);
        a.setRequestHash(hash);
        a.setDocumentLineId(documentLineId);
        a.setOperationId(operationId);
        a.setStockId(stock.getId());
        a.setMaterialId(stock.getMaterialId());
        a.setUnitId(unit.getId());
        a.setWarehouseId(stock.getWarehouseId());
        a.setBatchNo(stock.getBatchNo());
        a.setBinCode(stock.getBinCode());
        a.setActionType(type.getValue());
        a.setQuantityDelta(delta);
        a.setAvailableDelta(delta);
        a.setQuantityBefore(before);
        a.setQuantityAfter(after);
        a.setAvailableBefore(avail);
        a.setAvailableAfter(availableAfter);
        a.setSourceType(sourceType.getValue());
        a.setSourceId(sourceId);
        a.setReferenceActionId(reference);
        a.setExecutionId(execution);
        a.setOperatorUserId(actor);
        a.setOccurredAt(LocalDateTime.now());
        a.setRecordedAt(a.getOccurredAt());
        a.setReason(reason);
        stock.setQuantity(after);
        stock.setAvailableQuantity(availableAfter);
        stock.setUpdateBy(actor.toString());
        stock.setUpdateTime(a.getRecordedAt());
        if (inventory.updateByPrimaryKey(stock) != 1 || actions.insert(a) != 1) ParameterChecks.reject(ErrorCode.RESOURCE_CONFLICT);
        return a;
    }

    private WmStockActionEntity replay(String id, String hash) {
        var old = actions.selectByPrimaryKey(id);
        if (old != null && !hash.equals(old.getRequestHash())) ParameterChecks.reject(ErrorCode.RESOURCE_CONFLICT);
        return old;
    }

    private BigDecimal returned(String original) {
        // One original issue is serialized by its production chain lock; cap aggregation in SQL, not a growing list.
        return actions.sumReturns(original, StockActionType.RETURN.getValue());
    }

    private static void requireLocal(MesMaterialIssueInputEntity input, Calculation calculation) {
        if (!Mode.LOCAL_ONLY.getValue().equals(input.getSourceMode())
                || !Mode.LOCAL_ONLY.getValue().equals(calculation.sourceMode())) ParameterChecks.reject(ErrorCode.RESOURCE_CONFLICT);
    }

    private void identity(WmInventoryEntity s, Calculation c) {
        var source = c.stock();
        var material = materials.selectByPrimaryKey(s.getMaterialId());
        if (!Objects.equals(s.getId()
                .toString(), source.reference()) || !Objects.equals(s.getMaterialId(), source.materialId()) || !Objects.equals(s.getWarehouseId(), source.warehouseId())
                || !Objects.equals(s.getBatchNo(), source.lotCode()) || !Objects.equals(s.getBinCode(), source.binCode())
                || material == null || !Objects.equals(material.getUnitId(), source.unitId())) ParameterChecks.reject(ErrorCode.RESOURCE_CONFLICT);
    }

    private static BigDecimal available(WmInventoryEntity s) {
        return s.getAvailableQuantity() == null ? s.getQuantity() : s.getAvailableQuantity();
    }

    public static void validateBalances(BigDecimal quantity, BigDecimal available) {
        decimal(quantity);
        decimal(available);
        if (quantity.signum() < 0 || available.signum() < 0 || available.compareTo(quantity) > 0) ParameterChecks.reject(ErrorCode.RESOURCE_CONFLICT);
    }

    private static void decimal(BigDecimal value) {
        if (value == null || value.scale() > 6 || value.precision() - value.scale() > 14) ParameterChecks.reject(ErrorCode.BAD_REQUEST);
    }

    private static String normalized(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }

    private static String uuid(String value) {
        try {
            String id = UUID.fromString(value).toString();
            if (!id.equals(value)) ParameterChecks.reject(ErrorCode.BAD_REQUEST);
            return id;
        } catch (IllegalArgumentException | NullPointerException failure) {
            throw new FoundationException(ErrorCode.BAD_REQUEST, failure);
        }
    }

    private static String digest(Object... values) {
        return ProductionScanCode.digest(new ObjectMapper().writeValueAsString(Arrays.asList(values)));
    }


}
