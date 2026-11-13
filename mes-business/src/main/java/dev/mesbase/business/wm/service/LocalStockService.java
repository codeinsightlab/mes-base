package dev.mesbase.business.wm.service;

import dev.mesbase.business.basic.dao.MaterialMapper;
import dev.mesbase.business.basic.dao.UnitMeasureMapper;
import dev.mesbase.business.integration.warehouse.WarehouseIntegration;
import dev.mesbase.business.production.service.BusinessSourceRules;
import dev.mesbase.business.production.service.MaterialQuantityRules;
import dev.mesbase.business.shared.enums.BusinessSource;
import dev.mesbase.business.shared.enums.EnableStatus;
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
import dev.mesbase.business.wm.domain.WmInventoryResource;
import dev.mesbase.business.wm.domain.WmStockActionEntity;
import dev.mesbase.business.wm.domain.WmStockDocumentEntity;
import dev.mesbase.business.wm.domain.WmStockDocumentLineEntity;
import dev.mesbase.business.wm.domain.WmStockDocumentLineExample;
import dev.mesbase.common.utils.RequestFingerprint;
import dev.mesbase.common.utils.SecurityUtils;
import dev.mesbase.common.utils.StringUtils;
import dev.mesbase.common.validation.ParameterChecks;
import dev.mesbase.core.error.ErrorCode;
import dev.mesbase.core.error.FoundationException;
import dev.mesbase.core.error.SafeBusinessMessage;
import dev.mesbase.infrastructure.cache.annotation.BusinessCacheEvict;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/**
 * LOCAL余额唯一业务写入者. Callers acquire any upstream business locks before entering this owner.
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
    private final WmStockDocumentLineMapper documentLines;
    private final WarehouseIntegration integration;
    private final MaterialQuantityRules quantityRules;
    private final StockActionRuleRegistry rules;

    public LocalStockService(WmInventoryMapper inventory, WmStockActionMapper actions, MaterialMapper materials,
                             UnitMeasureMapper units, WmWarehouseMapper warehouses,
                             WmStockDocumentLineMapper documentLines,
                             WmStockDocumentMapper documents, WarehouseIntegration integration,
                             BusinessSourceRules sources, MaterialQuantityRules quantityRules) {
        this.sources = sources;
        this.quantityRules = quantityRules;
        this.inventory = inventory;
        this.actions = actions;
        this.materials = materials;
        this.units = units;
        this.warehouses = warehouses;
        this.documentLines = documentLines;
        this.integration = integration;
        this.rules = new StockActionRuleRegistry(actions, documentLines, documents);
    }

    @Transactional(transactionManager = "businessTransactionManager", isolation = Isolation.READ_COMMITTED, rollbackFor = Exception.class)
    @BusinessCacheEvict(business = WmInventoryResource.PREFIX, scope = BusinessCacheEvict.Scope.LIST_AND_DETAIL)
    public WmStockActionEntity execute(StockActionType type, StockCommand c) {
        var rule = rules.manual(type);
        String id = uuid(c.requestId());
        decimal(c.quantity());
        decimal(c.expectedQuantity());
        decimal(c.expectedAvailable());
        if (StringUtils.isBlank(c.reason()) || c.reason().length() > 500 || c.stockId() == null)
            ParameterChecks.reject(ErrorCode.BAD_REQUEST, new SafeBusinessMessage("库存操作必须填写有效库存ID和不超过500个字符的原因"));
        rule.validateParameters(c);
        Long actor = SecurityUtils.getUserId();
        Long requestIdentity = c.documentId();
        String hash = digest(type, c.stockId(), normalized(c.quantity()), normalized(c.expectedQuantity()),
                normalized(c.expectedAvailable()), c.reason(), requestIdentity, actor);
        if (c.documentLineId() != null) hash = digest(hash, c.documentLineId());
        var replayed = replay(id, hash);
        if (replayed != null) return replayed;
        var identity = inventory.selectByPrimaryKey(c.stockId());
        if (identity == null) throw new FoundationException(ErrorCode.NOT_FOUND);
        quantityRules.lockForWrite(List.of(identity.getMaterialId()));
        identity = inventory.selectByPrimaryKey(c.stockId());
        if (identity == null) throw new FoundationException(ErrorCode.NOT_FOUND);
        var stock = lock(c.stockId());
        if (!Objects.equals(identity.getMaterialId(), stock.getMaterialId()))
            ParameterChecks.reject(ErrorCode.RESOURCE_CONFLICT);
        var old = replay(id, hash);
        if (old != null) return old;
        rule.checkQuantity(c, stock, quantityRules);
        var plan = rule.plan(c, stock);
        if (plan.originalLine() == null) sources.warehouse(BusinessSource.LOCAL.getValue(), stock.getWarehouseId());
        else {
            sources.frozenLocalWarehouse(stock.getWarehouseId());
            identity(stock, plan.originalLine());
        }
        if (stock.getQuantity()
                .compareTo(c.expectedQuantity()) != 0 || available(stock).compareTo(c.expectedAvailable()) != 0)
            ParameterChecks.reject(ErrorCode.RESOURCE_CONFLICT);
        return change(stock, id, hash, type, plan.delta(), plan.sourceType(), plan.sourceId(),
                plan.referenceActionId(), plan.executionCode(), actor, c.reason());
    }

    /**
     * Reverse the stock effects referenced by one posted stock document. Business lifecycle checks
     * belong to the caller; this owner validates only the document's stock facts and balances.
     */
    @Transactional(transactionManager = "businessTransactionManager", propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    @BusinessCacheEvict(business = WmInventoryResource.PREFIX, scope = BusinessCacheEvict.Scope.LIST_AND_DETAIL)
    public void reverseDocument(WmStockDocumentEntity document, String operationId, String reason) {
        uuid(operationId);
        var filter = new WmStockDocumentLineExample();
        filter.createCriteria().andDocumentIdEqualTo(document.getId());
        var rows = documentLines.selectByExample(filter).stream()
                .sorted(Comparator.comparing(WmStockDocumentLineEntity::getStockId)
                        .thenComparing(WmStockDocumentLineEntity::getId)).toList();
        if (rows.isEmpty()) ParameterChecks.reject(ErrorCode.RESOURCE_CONFLICT);
        quantityRules.lockForWrite(rows.stream().map(WmStockDocumentLineEntity::getMaterialId).distinct().sorted()
                .toList());
        for (var line : rows) lock(line.getStockId());
        Long actor = SecurityUtils.getUserId();
        for (var line : rows) {
            sources.frozenLocalWarehouse(line.getWarehouseId());
            var stock = lock(line.getStockId());
            identity(stock, line);
            String id = operationId;
            if (!line.getId().equals(rows.getFirst().getId()))
                id = UUID.nameUUIDFromBytes((operationId + ":" + line.getId())
                        .getBytes(StandardCharsets.UTF_8)).toString();
            String hash = digest(StockActionType.REVERSAL, line.getStockActionId(), operationId,
                    actor, reason);
            if (line.getStockActionId() == null) ParameterChecks.reject(ErrorCode.RESOURCE_CONFLICT);
            var original = actions.selectByPrimaryKey(line.getStockActionId());
            if (original == null
                    || actions.sumReturns(original.getId(), StockActionType.REVERSAL.getValue()).signum() != 0)
                ParameterChecks.reject(ErrorCode.RESOURCE_CONFLICT);
            if (replay(id, hash) != null) ParameterChecks.reject(ErrorCode.RESOURCE_CONFLICT);
            var plan = StockActionRuleRegistry.DocumentReversalRule.plan(document, line, original,
                    returned(original.getId()));
            if (plan.delta().signum() == 0) continue;
            change(stock, id, hash, StockActionType.REVERSAL, plan.delta(), plan.sourceType(),
                    plan.sourceId(), plan.referenceActionId(), plan.executionCode(),
                    actor, reason, line.getId(), operationId);
        }
    }

    /**
     * Caller holds WorkOrder -> Execution before entering this owner; only the owner locks/writes stock.
     */
    /**
     * The document transaction owns all lines; this owner alone mutates balances and appends facts.
     */
    @Transactional(transactionManager = "businessTransactionManager", propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    @BusinessCacheEvict(business = WmInventoryResource.PREFIX, scope = BusinessCacheEvict.Scope.LIST_AND_DETAIL)
    public WmStockActionEntity postDocument(String direction, String type, Long documentId,
                                            WmStockDocumentLineEntity line,
                                            String operationId, String actionId, String requestHash, String reason,
                                            String executionCode) {
        var rule = StockActionRuleRegistry.DocumentPostingRule.resolve(direction, type);
        uuid(operationId);
        uuid(actionId);
        decimal(line.getQuantity());
        if (line.getQuantity().signum() <= 0 || !Objects.equals(documentId, line.getDocumentId()))
            ParameterChecks.reject(ErrorCode.BAD_REQUEST, new SafeBusinessMessage("库存单明细数量必须大于零且属于当前库存单"));
        var stock = lock(line.getStockId());
        sources.warehouse(BusinessSource.LOCAL.getValue(), stock.getWarehouseId());
        var material = materials.selectByPrimaryKey(stock.getMaterialId());
        if (!Objects.equals(stock.getMaterialId(), line.getMaterialId())
                || !Objects.equals(stock.getWarehouseId(), line.getWarehouseId())
                || !Objects.equals(stock.getBatchNo(), line.getBatchNo())
                || !Objects.equals(stock.getBinCode(), line.getBinCode())
                || material == null || !Objects.equals(material.getUnitId(), line.getUnitId()))
            ParameterChecks.reject(ErrorCode.RESOURCE_CONFLICT);
        if (rule.direction() == StockDocumentDirection.OUT && (stock.getQuantity().compareTo(line.getQuantity()) < 0
                || available(stock).compareTo(line.getQuantity()) < 0))
            throw new FoundationException(ErrorCode.STOCK_INSUFFICIENT);
        BigDecimal delta = rule.delta(line.getQuantity());
        String sourceId = documentId.toString();

        return change(stock, actionId, requestHash, rule.actionType(), delta, rule.sourceType(), sourceId,
                null, executionCode, SecurityUtils.getUserId(), reason, line.getId(), operationId);
    }

    @Transactional(transactionManager = "businessTransactionManager", propagation = Propagation.MANDATORY)
    public WmInventoryEntity lock(Long id) {
        if (id == null) ParameterChecks.reject(ErrorCode.RESOURCE_CONFLICT);
        var stock = inventory.selectByPrimaryKeyForUpdate(id);
        if (stock == null) ParameterChecks.reject(ErrorCode.RESOURCE_CONFLICT);
        validateBalances(stock.getQuantity(), available(stock));
        return stock;
    }

    private WmStockActionEntity change(WmInventoryEntity stock, String id, String hash, StockActionType type,
                                       BigDecimal delta, StockSourceType sourceType, String sourceId, String reference,
                                       String execution,
                                       Long actor, String reason) {
        return change(stock, id, hash, type, delta, sourceType, sourceId, reference, execution, actor, reason,
                null, null);
    }

    private WmStockActionEntity change(WmInventoryEntity stock, String id, String hash, StockActionType type,
                                       BigDecimal delta, StockSourceType sourceType, String sourceId, String reference,
                                       String execution, Long actor, String reason, Long documentLineId,
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
                || !EnableStatus.ENABLED.getValue().equals(unit.getStatus()))
            ParameterChecks.reject(ErrorCode.RESOURCE_CONFLICT);
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
        a.setExecutionCode(execution);
        a.setOperatorUserId(actor);
        a.setOccurredAt(LocalDateTime.now());
        a.setRecordedAt(a.getOccurredAt());
        a.setReason(reason);
        stock.setQuantity(after);
        stock.setAvailableQuantity(availableAfter);
        stock.setUpdateBy(actor.toString());
        stock.setUpdateTime(a.getRecordedAt());
        if (inventory.updateByPrimaryKey(stock) != 1 || actions.insert(a) != 1)
            ParameterChecks.reject(ErrorCode.RESOURCE_CONFLICT);
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

    private void identity(WmInventoryEntity stock, WmStockDocumentLineEntity line) {
        var material = materials.selectByPrimaryKey(stock.getMaterialId());
        if (!Objects.equals(stock.getId(), line.getStockId())
                || !Objects.equals(stock.getMaterialId(), line.getMaterialId())
                || !Objects.equals(stock.getWarehouseId(), line.getWarehouseId())
                || !Objects.equals(stock.getBatchNo(), line.getBatchNo())
                || !Objects.equals(stock.getBinCode(), line.getBinCode())
                || material == null || !Objects.equals(material.getUnitId(), line.getUnitId()))
            ParameterChecks.reject(ErrorCode.RESOURCE_CONFLICT);
    }

    private static BigDecimal available(WmInventoryEntity s) {
        return s.getAvailableQuantity() == null ? s.getQuantity() : s.getAvailableQuantity();
    }

    public static void validateBalances(BigDecimal quantity, BigDecimal available) {
        decimal(quantity);
        decimal(available);
        if (quantity.signum() < 0)
            ParameterChecks.reject(ErrorCode.RESOURCE_CONFLICT, new SafeBusinessMessage("本次操作会使库存实存数量变为负数"));
        if (available.signum() < 0)
            ParameterChecks.reject(ErrorCode.RESOURCE_CONFLICT, new SafeBusinessMessage("本次操作会使库存可用数量变为负数"));
        if (available.compareTo(quantity) > 0)
            ParameterChecks.reject(ErrorCode.RESOURCE_CONFLICT, new SafeBusinessMessage("库存可用数量不能大于实存数量"));
    }

    private static void decimal(BigDecimal value) {
        if (value == null || value.scale() > 6 || value.precision() - value.scale() > 14)
            ParameterChecks.reject(ErrorCode.BAD_REQUEST, new SafeBusinessMessage("库存数量最多支持14位整数和6位小数"));
    }

    private static String normalized(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }

    private static String uuid(String value) {
        try {
            String id = UUID.fromString(value).toString();
            if (!id.equals(value))
                ParameterChecks.reject(ErrorCode.BAD_REQUEST, new SafeBusinessMessage("请求标识格式无效，请使用标准UUID"));
            return id;
        } catch (IllegalArgumentException | NullPointerException failure) {
            throw new FoundationException(ErrorCode.BAD_REQUEST, failure,
                    new SafeBusinessMessage("请求标识格式无效，请使用标准UUID"));
        }
    }

    private static String digest(Object... values) {
        return RequestFingerprint.sha256(new ObjectMapper().writeValueAsString(Arrays.asList(values)));
    }


}
