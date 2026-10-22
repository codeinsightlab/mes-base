package dev.mesbase.business.legacy.service;

import dev.mesbase.common.validation.ParameterChecks;
import dev.mesbase.business.legacy.dao.LegacyMigrationMapper;
import dev.mesbase.business.legacy.domain.LegacyHistory.Batch;
import dev.mesbase.business.legacy.domain.LegacyBatchStatus;
import dev.mesbase.business.shared.enums.BusinessSource;
import dev.mesbase.business.legacy.domain.LegacyHistory.Mapping;
import dev.mesbase.business.production.dao.MesProductionExecutionMapper;
import dev.mesbase.business.production.dao.WorkorderMapper;
import dev.mesbase.business.production.domain.ProductionCompletionRequested;
import dev.mesbase.business.production.domain.ProductionExecutionStatus;
import dev.mesbase.business.production.domain.WorkOrderStatus;
import dev.mesbase.business.production.service.BusinessSourceRules;
import dev.mesbase.business.production.service.ProductionQuantityService;
import dev.mesbase.common.utils.SecurityUtils;
import dev.mesbase.core.error.ErrorCode;
import dev.mesbase.core.error.FoundationException;
import dev.mesbase.platform.execution.FactoryContext;
import dev.mesbase.infrastructure.cache.annotation.BusinessCacheEvict;
import dev.mesbase.business.production.domain.MesProductionExecutionResource;
import java.math.BigDecimal;
import java.util.Comparator;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reviewed opening quantities join the same production lifecycle; no stock or issue is manufactured.
 */
@Service
@ConditionalOnProperty(name = "mes.business.enabled", havingValue = "true")
public class LegacyProductionActivationService {
    public static final String RULE = "internal-production-activation-v1";
    private final LegacyMigrationMapper metadata;
    private final WorkorderMapper orders;
    private final MesProductionExecutionMapper executions;
    private final ProductionQuantityService quantities;
    private final BusinessSourceRules sources;
    private final ApplicationEventPublisher events;

    public LegacyProductionActivationService(LegacyMigrationMapper metadata, WorkorderMapper orders,
                                             MesProductionExecutionMapper executions,
                                             ProductionQuantityService quantities, BusinessSourceRules sources,
                                             ApplicationEventPublisher events) {
        this.metadata = metadata;
        this.orders = orders;
        this.executions = executions;
        this.quantities = quantities;
        this.sources = sources;
        this.events = events;
    }

    @Transactional(transactionManager = "businessTransactionManager", isolation = Isolation.READ_COMMITTED, rollbackFor = Exception.class)
    @BusinessCacheEvict(business = MesProductionExecutionResource.PREFIX, scope = BusinessCacheEvict.Scope.LIST_AND_DETAIL)
    public int activate(Long id) {
        sources.localCreation();
        Long actor = SecurityUtils.getUserId();
        if (actor == null) ParameterChecks.reject(ErrorCode.RESOURCE_CONFLICT);
        var order = orders.selectByPrimaryKeyForUpdate(id);
        var original = metadata.order(id);
        if (order == null || original == null || !BusinessSource.LOCAL.getValue().equals(order.getSource())) ParameterChecks.reject(ErrorCode.RESOURCE_CONFLICT);
        String orderKey = key(original.id());
        if (metadata.find(orderKey) != null) return 0;
        if (!WorkOrderStatus.HISTORICAL.getValue().equals(order.getStatus())) ParameterChecks.reject(ErrorCode.RESOURCE_CONFLICT);
        var mappings = metadata.orderExecutions(original.id()).stream()
                .sorted(Comparator.comparing(Mapping::entityId)).toList();
        if (mappings.isEmpty()) ParameterChecks.reject(ErrorCode.RESOURCE_CONFLICT);
        var now = LocalDateTime.now();
        String batchId = "activate-" + id;
        metadata.prepare(new Batch(batchId, RULE, original.sourceHash(), LegacyBatchStatus.PREPARED.getValue(), "[\"" + orderKey + "\"]", actor, now, null));
        var batch = metadata.batch(batchId);
        if (!RULE.equals(batch.ruleVersion()) || !original.sourceHash().equals(batch.snapshotHash())
                || !LegacyBatchStatus.PREPARED.getValue().equals(batch.status())) ParameterChecks.reject(ErrorCode.RESOURCE_CONFLICT);
        if (metadata.transition(batchId, LegacyBatchStatus.PREPARED.getValue(), LegacyBatchStatus.RUNNING.getValue(), LegacyBatchStatus.COMPLETED.getValue()) != 1)
            ParameterChecks.reject(ErrorCode.RESOURCE_CONFLICT);
        var allocated = BigDecimal.ZERO;
        // Bound batch projections; retain every original node quantity before enabling writes.
        for (int from = 0; from < mappings.size(); from += 100) {
            var portion = mappings.subList(from, Math.min(from + 100, mappings.size()));
            var projections = quantities.getHistoricalExecutionQuantities(portion.stream().map(Mapping::entityId)
                    .toList());
            for (var m : portion) {
                var row = executions.selectByPrimaryKeyForUpdate(m.entityId());
                var q = projections.get(m.entityId());
                if (row == null || !id.equals(row.getWorkorderId()) || !ProductionExecutionStatus.HISTORICAL.getValue()
                        .equals(row.getStatus())
                        || q == null || !q.historical() || q.nodes().isEmpty()
                        || q.productionInputQty().signum() <= 0 || q.confirmedInputQty().signum() != 0
                        || q.reservedInputQty().signum() != 0 || q.productionInputQty()
                        .compareTo(row.getTargetQty()) != 0
                        || q.nodes().stream()
                        .anyMatch(n -> n.historicalConflict() || n.unverifiedDefectQty().signum() != 0)) ParameterChecks.reject(ErrorCode.RESOURCE_CONFLICT);
                allocated = allocated.add(row.getTargetQty());
                add(m, batchId, "PRODUCTION_ACTIVATION", now, actor);
            }
        }
        if (allocated.compareTo(order.getPlannedQty()) > 0) ParameterChecks.reject(ErrorCode.RESOURCE_CONFLICT);
        add(original, batchId, "ORDER_ACTIVATION", now, actor);
        order.setStatus(WorkOrderStatus.RELEASED.getValue());
        order.setRevision(order.getRevision() + 1);
        order.setUpdateTime(now);
        if (orders.updateByPrimaryKey(order) != 1) ParameterChecks.reject(ErrorCode.RESOURCE_CONFLICT);
        for (var m : mappings) {
            var row = executions.selectByPrimaryKeyForUpdate(m.entityId());
            row.setStatus(ProductionExecutionStatus.RUNNING.getValue());
            row.setStartTime(now);
            row.setUpdateTime(now);
            row.setUpdateBy(actor.toString());
            if (executions.updateByPrimaryKey(row) != 1) ParameterChecks.reject(ErrorCode.RESOURCE_CONFLICT);
            // Normal post-commit completion, not a copied external completion status.
            events.publishEvent(new ProductionCompletionRequested(FactoryContext.required(), row.getId()));
        }
        if (metadata.transition(batchId, LegacyBatchStatus.RUNNING.getValue(), LegacyBatchStatus.COMPLETED.getValue(), LegacyBatchStatus.COMPLETED.getValue()) != 1)
            ParameterChecks.reject(ErrorCode.RESOURCE_CONFLICT);
        return mappings.size();
    }

    private void add(Mapping original, String batch, String kind, LocalDateTime now, Long actor) {
        String evidence = "{\"operatorUserId\":\"" + actor + "\",\"basis\":\"REVIEWED_OPENING_QUANTITIES\"}";
        if (metadata.insert(new Mapping(key(original.id()), batch, original.id(), "PRODUCTION_ACTIVATION",
                original.id(), original.sourceHash(), RULE, kind, original.entityId(), evidence, "{}", now)) != 1)
            ParameterChecks.reject(ErrorCode.RESOURCE_CONFLICT);
    }

    private static String key(String original) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest((RULE + ":" + original).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException failure) {
            throw new IllegalStateException(failure);
        }
    }

}
