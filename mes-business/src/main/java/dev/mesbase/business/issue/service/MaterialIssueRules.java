package dev.mesbase.business.issue.service;

import dev.mesbase.business.basic.dao.MaterialMapper;
import dev.mesbase.business.basic.dao.UnitMeasureMapper;
import dev.mesbase.business.basic.domain.MaterialExample;
import dev.mesbase.business.integration.warehouse.ExternalEffect;
import dev.mesbase.business.issue.dao.MesMaterialIssueItemMapper;
import dev.mesbase.business.issue.dao.MesMaterialIssueMapper;
import dev.mesbase.business.issue.domain.MaterialIssueStatus;
import dev.mesbase.business.issue.domain.MesMaterialIssueEntity;
import dev.mesbase.business.issue.domain.MesMaterialIssueExample;
import dev.mesbase.business.issue.domain.MesMaterialIssueItemEntity;
import dev.mesbase.business.issue.domain.MesMaterialIssueItemExample;
import dev.mesbase.business.production.dao.RoutingMapper;
import dev.mesbase.business.production.dao.RoutingProductMapper;
import dev.mesbase.business.production.dao.WorkorderMapper;
import dev.mesbase.business.production.domain.MesProductionExecutionEntity;
import dev.mesbase.business.production.domain.ProductionExecutionStatus;
import dev.mesbase.business.production.domain.RoutingProductExample;
import dev.mesbase.business.production.service.BusinessSourceRules;
import dev.mesbase.business.production.service.ProductionChainLocks;
import dev.mesbase.business.production.service.ProductionExecutionRules;
import dev.mesbase.business.wm.dao.WmWarehouseMapper;
import dev.mesbase.common.utils.SecurityUtils;
import dev.mesbase.common.utils.StringUtils;
import dev.mesbase.business.shared.enums.EnableStatus;
import dev.mesbase.core.error.ErrorCode;
import dev.mesbase.core.error.FoundationException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Minimal reference rules for generated administrative CRUD. No ERP or stock effects.
 */
@Component
@ConditionalOnProperty(name = "mes.business.enabled", havingValue = "true")
public class MaterialIssueRules {
    private final BusinessSourceRules sources;
    private final MesMaterialIssueMapper issues;
    private final MesMaterialIssueItemMapper items;
    private final MaterialMapper materials;
    private final UnitMeasureMapper units;
    private final WmWarehouseMapper warehouses;
    private final WorkorderMapper workorders;
    private final RoutingMapper routings;
    private final RoutingProductMapper routingProducts;
    private final ProductionExecutionRules executions;
    private final ProductionChainLocks locks;
    private final MaterialIssueErpFacts facts;

    public MaterialIssueRules(MesMaterialIssueMapper issues, MesMaterialIssueItemMapper items,
                              MaterialMapper materials, UnitMeasureMapper units, WmWarehouseMapper warehouses,
                              WorkorderMapper workorders, RoutingMapper routings,
                              RoutingProductMapper routingProducts, ProductionExecutionRules executions,
                              MaterialIssueErpFacts facts, ProductionChainLocks locks, BusinessSourceRules sources) {
        this.sources = sources;
        this.locks = locks;
        this.issues = issues;
        this.items = items;
        this.materials = materials;
        this.units = units;
        this.warehouses = warehouses;
        this.workorders = workorders;
        this.routings = routings;
        this.routingProducts = routingProducts;
        this.executions = executions;
        this.facts = facts;
    }

    private void require(boolean valid) {
        if (!valid) throw new FoundationException(ErrorCode.BAD_REQUEST);
    }

    public MesMaterialIssueEntity hydrate(MesMaterialIssueEntity row) {
        return facts.hydrate(row);
    }

    public void hydrate(List<MesMaterialIssueEntity> rows) {
        facts.hydrate(rows);
    }

    public void validateExecutionSource(MesProductionExecutionEntity execution) {
        sources.execution(execution, null);
    }

    public void initializeExtra(MesMaterialIssueEntity row) {
        facts.initialize(row);
    }

    public Long extraVersion(Long id) {
        return facts.required(id).getOperationVersion();
    }

    public void changedExtra(List<Long> ids) {
        for (Long id : ids) facts.changed(id);
    }

    public void deleteExtra(List<Long> ids) {
        facts.delete(ids);
    }

    public void validate(MesMaterialIssueEntity row) {
        require(row.getStatus() != null && Set.of(MaterialIssueStatus.DRAFT.getValue(), MaterialIssueStatus.RECORDED.getValue())
                .contains(row.getStatus()));
        require(StringUtils.isNotBlank(row.getIssueNo()));
        if (row.getSubmissionId() != null || row.getVoidOperationId() != null || !Set.of(MaterialIssueStatus.DRAFT.getValue(), MaterialIssueStatus.RECORDED.getValue())
                .contains(row.getStatus()))
            throw new FoundationException(ErrorCode.ISSUE_VOID_CONFLICT);
        var execution = Optional.ofNullable(row.getExecutionId()).map(nullableValue -> executions.lock(nullableValue))
                .orElse(null);
        if (execution != null) {
            if (!ProductionExecutionStatus.allowsNormalReporting(execution.getStatus()))
                throw new FoundationException(ErrorCode.EXECUTION_FROZEN);
            require(row.getWorkorderId() == null || Objects.equals(row.getWorkorderId(), execution.getWorkorderId()));
            require(row.getProductId() == null || Objects.equals(row.getProductId(), execution.getProductId()));
            require(row.getRoutingId() == null || Objects.equals(row.getRoutingId(), execution.getRoutingId()));
            row.setWorkorderId(execution.getWorkorderId());
            row.setProductId(execution.getProductId());
            row.setRoutingId(execution.getRoutingId());
        }
        if (row.getProductId() != null) {
            var product = materials.selectByPrimaryKey(row.getProductId());
            require(product != null);
            row.setProductCode(product.getCode());
        }
        if (row.getWorkorderId() != null) {
            // WorkOrder belongs to the request-pinned business database.
            var workorder = workorders.selectByPrimaryKeyForUpdate(row.getWorkorderId());
            require(workorder != null);
            sources.order(workorder.getId());
            require(row.getProductId() == null || Objects.equals(row.getProductId(), workorder.getProductId()));
            require(execution != null || row.getRoutingId() == null || Objects.equals(row.getRoutingId(), workorder.getRoutingId()));
            var product = materials.selectByPrimaryKey(workorder.getProductId());
            require(product != null);
            row.setProductId(product.getId());
            row.setProductCode(product.getCode());
            row.setWorkorderCode(workorder.getCode());
            if (execution == null) row.setRoutingId(workorder.getRoutingId());
        }
        if (row.getRoutingId() != null) {
            var routing = routings.selectByPrimaryKey(row.getRoutingId());
            require(routing != null
                    && routing.getPublishedAt() != null);
            // A previously published disabled version is still a stable historical reference.
            if (row.getProductId() == null && StringUtils.isNotBlank(row.getProductCode())) {
                var byCode = new MaterialExample();
                byCode.createCriteria().andCodeEqualTo(row.getProductCode());
                var matches = materials.selectByExample(byCode);
                require(matches.size() == 1);
                row.setProductId(matches.getFirst().getId());
                row.setProductCode(matches.getFirst().getCode());
            }
            if (row.getProductId() != null) {
                var applicable = new RoutingProductExample();
                applicable.createCriteria().andRoutingIdEqualTo(row.getRoutingId())
                        .andMaterialIdEqualTo(row.getProductId());
                require(routingProducts.countByExample(applicable) > 0);
            }
        }
    }

    public void validate(MesMaterialIssueItemEntity row) {
        validate(row, false);
    }

    public void validateCalculated(MesMaterialIssueItemEntity row) {
        validate(row, true);
    }

    private void validate(MesMaterialIssueItemEntity row, boolean calculated) {
        require(row.getIssueId() != null && row.getMaterialId() != null && row.getQuantity() != null);
        lockIssues(List.of(row.getIssueId()));
        var header = issues.selectByPrimaryKey(row.getIssueId());
        if (header.getSubmissionId() != null || header.getVoidOperationId() != null || !Set.of(MaterialIssueStatus.DRAFT.getValue(), MaterialIssueStatus.RECORDED.getValue())
                .contains(header.getStatus()))
            throw new FoundationException(ErrorCode.ISSUE_VOID_CONFLICT);
        require(row.getQuantity().signum() > 0 && row.getQuantity().scale() <= 6
                && row.getQuantity().precision() - row.getQuantity().scale() <= 14);
        var material = materials.selectByPrimaryKeyForUpdate(row.getMaterialId());
        require(material != null);
        if (calculated) require(EnableStatus.ENABLED.getValue().equals(material.getStatus())
                && Objects.equals(material.getUnitId(), row.getUnitId()));
        row.setMaterialCode(material.getCode());
        row.setUnitCode(null);
        if (row.getUnitId() != null) {
            var unit = units.selectByPrimaryKeyForUpdate(row.getUnitId());
            require(unit != null);
            if (calculated) require(EnableStatus.ENABLED.getValue().equals(unit.getStatus()));
            row.setUnitCode(unit.getCode());
        }
        row.setWarehouseCode(null);
        if (row.getWarehouseId() != null) {
            var warehouse = warehouses.selectByPrimaryKey(row.getWarehouseId());
            require(warehouse != null);
            if (header.getWorkorderId() == null) sources.warehouse(warehouse.getSource(), warehouse.getId());
            else sources.warehouse(sources.order(header.getWorkorderId()).getSource(), warehouse.getId());
            row.setWarehouseCode(warehouse.getCode());
        }
    }

    public void lockIssueUpdate(Long id, Long workorderId, Long executionId) {
        locks.issueUpdate(id, workorderId, executionId);
    }

    public void lockIssues(List<Long> ids) {
        locks.issues(ids);
    }

    public void requireEditableIssues(List<Long> ids) {
        lockIssues(ids);
        for (Long id : ids) {
            var row = issues.selectByPrimaryKey(id);
            if (row.getSubmissionId() != null || row.getVoidOperationId() != null || !Set.of(MaterialIssueStatus.DRAFT.getValue(), MaterialIssueStatus.RECORDED.getValue())
                    .contains(row.getStatus()))
                throw new FoundationException(ErrorCode.ISSUE_VOID_CONFLICT);
        }
    }

    public void deletingIssues(List<Long> ids) {
        lockIssues(ids);
        var externalRows = new MesMaterialIssueExample();
        externalRows.createCriteria().andIdIn(ids);
        var loaded = issues.selectByExample(externalRows);
        facts.hydrate(loaded);
        if (loaded.stream().anyMatch(r -> !ExternalEffect.NONE.getValue().equals(r.getExternalEffect())))
            throw new FoundationException(ErrorCode.ISSUE_EXTERNAL_UNVERIFIED);
        if (loaded.stream().anyMatch(r -> r.getSubmissionId() != null))
            throw new FoundationException(ErrorCode.RESOURCE_CONFLICT);
        var protectedRows = new MesMaterialIssueExample();
        protectedRows.createCriteria().andIdIn(ids).andVoidOperationIdIsNotNull();
        if (issues.countByExample(protectedRows) > 0) throw new FoundationException(ErrorCode.ISSUE_VOID_CONFLICT);
        // Keep the execution fact relationship: physical deletion must not erase the freeze evidence.
        var headers = new MesMaterialIssueExample();
        headers.createCriteria().andIdIn(ids).andExecutionIdIsNotNull();
        if (issues.countByExample(headers) > 0) throw new FoundationException(ErrorCode.EXECUTION_FROZEN);
        var filter = new MesMaterialIssueItemExample();
        filter.createCriteria().andIssueIdIn(ids);
        if (items.countByExample(filter) != 0) {
            throw new FoundationException(ErrorCode.RESOURCE_CONFLICT);
        }
    }

    public void audit(MesMaterialIssueEntity row, boolean created) {
        String user = String.valueOf(SecurityUtils.getUserId());
        var now = LocalDateTime.now();
        if (created) {
            row.setCreateBy(user);
            row.setCreateTime(now);
        }
        row.setUpdateBy(user);
        row.setUpdateTime(now);
        if (!created && row.getId() != null) facts.saveProjection(row);
    }

    public void audit(MesMaterialIssueItemEntity row, boolean created) {
        String user = String.valueOf(SecurityUtils.getUserId());
        var now = LocalDateTime.now();
        if (created) {
            row.setCreateBy(user);
            row.setCreateTime(now);
        }
        row.setUpdateBy(user);
        row.setUpdateTime(now);
        facts.changed(row.getIssueId());
    }
}
