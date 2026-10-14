package dev.mesbase.business.issue.service;

import dev.mesbase.business.basic.dao.MaterialMapper;
import dev.mesbase.business.basic.dao.UnitMeasureMapper;
import dev.mesbase.business.issue.dao.MesMaterialIssueMapper;
import dev.mesbase.business.issue.dao.MesMaterialIssueItemMapper;
import dev.mesbase.business.issue.domain.MesMaterialIssueEntity;
import dev.mesbase.business.issue.domain.MesMaterialIssueExample;
import dev.mesbase.business.issue.domain.MesMaterialIssueItemEntity;
import dev.mesbase.business.issue.domain.MesMaterialIssueItemExample;
import dev.mesbase.business.production.dao.WorkorderMapper;
import dev.mesbase.business.production.domain.WorkorderEntity;
import dev.mesbase.business.production.domain.WorkorderExample;
import dev.mesbase.business.production.dao.RoutingMapper;
import dev.mesbase.business.production.dao.RoutingProductMapper;
import dev.mesbase.business.production.domain.RoutingProductExample;
import dev.mesbase.business.wm.dao.WmWarehouseMapper;
import dev.mesbase.common.utils.SecurityUtils;
import dev.mesbase.core.error.ErrorCode;
import dev.mesbase.core.error.FoundationException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Minimal reference rules for generated administrative CRUD. No ERP or stock effects.
 */
@Component
@ConditionalOnProperty(name = "mes.business.enabled", havingValue = "true")
public class MaterialIssueRules {
    private final MesMaterialIssueMapper issues;
    private final MesMaterialIssueItemMapper items;
    private final MaterialMapper materials;
    private final UnitMeasureMapper units;
    private final WmWarehouseMapper warehouses;
    private final WorkorderMapper workorders;
    private final RoutingMapper routings;
    private final RoutingProductMapper routingProducts;

    public MaterialIssueRules(MesMaterialIssueMapper issues, MesMaterialIssueItemMapper items,
                              MaterialMapper materials, UnitMeasureMapper units, WmWarehouseMapper warehouses,
                              WorkorderMapper workorders, RoutingMapper routings,
                              RoutingProductMapper routingProducts) {
        this.issues = issues;
        this.items = items;
        this.materials = materials;
        this.units = units;
        this.warehouses = warehouses;
        this.workorders = workorders;
        this.routings = routings;
        this.routingProducts = routingProducts;
    }

    private void require(boolean valid) {
        if (!valid) throw new FoundationException(ErrorCode.BAD_REQUEST);
    }

    public void validate(MesMaterialIssueEntity row) {
        require(row.getStatus() != null && Set.of("DRAFT", "RECORDED").contains(row.getStatus()));
        require(row.getIssueNo() != null && !row.getIssueNo().isBlank());
        if (row.getProductId() != null) {
            var product = materials.selectByPrimaryKey(row.getProductId());
            require(product != null);
            row.setProductCode(product.getCode());
        }
        if (row.getWorkorderId() != null) {
            // WorkOrder belongs to the request-pinned business database.
            var filter = new WorkorderExample();
            filter.createCriteria().andIdEqualTo(row.getWorkorderId());
            var lock = new WorkorderEntity();
            lock.setId(row.getWorkorderId());
            require(workorders.updateByExampleSelective(lock, filter) == 1);
            var workorder = workorders.selectByPrimaryKey(row.getWorkorderId());
            require(workorder != null);
            require(row.getProductId() == null || Objects.equals(row.getProductId(), workorder.getProductId()));
            require(row.getRoutingId() == null || Objects.equals(row.getRoutingId(), workorder.getRoutingId()));
            var product = materials.selectByPrimaryKey(workorder.getProductId());
            require(product != null);
            row.setProductId(product.getId());
            row.setProductCode(product.getCode());
            row.setWorkorderCode(workorder.getCode());
            row.setRoutingId(workorder.getRoutingId());
        }
        if (row.getRoutingId() != null) {
            var routing = routings.selectByPrimaryKey(row.getRoutingId());
            require(routing != null
                    && routing.getPublishedAt() != null);
            // A previously published disabled version is still a stable historical reference.
            if (row.getProductCode() != null && !row.getProductCode().isBlank()) {
                var applicable = new RoutingProductExample();
                applicable.createCriteria().andRoutingIdEqualTo(row.getRoutingId())
                        .andMaterialCodeEqualTo(row.getProductCode());
                require(routingProducts.countByExample(applicable) > 0);
            }
        }
    }

    public void validate(MesMaterialIssueItemEntity row) {
        require(row.getIssueId() != null && row.getMaterialId() != null && row.getQuantity() != null);
        lockIssues(List.of(row.getIssueId()));
        require(row.getQuantity().signum() > 0 && row.getQuantity().scale() <= 6
                && row.getQuantity().precision() - row.getQuantity().scale() <= 14);
        var material = materials.selectByPrimaryKey(row.getMaterialId());
        require(material != null);
        row.setMaterialCode(material.getCode());
        row.setUnitCode(null);
        if (row.getUnitId() != null) {
            var unit = units.selectByPrimaryKey(row.getUnitId());
            require(unit != null);
            row.setUnitCode(unit.getCode());
        }
        row.setWarehouseCode(null);
        if (row.getWarehouseId() != null) {
            var warehouse = warehouses.selectByPrimaryKey(row.getWarehouseId());
            require(warehouse != null);
            row.setWarehouseCode(warehouse.getCode());
        }
    }

    public void lockIssues(List<Long> ids) {
        // Administrative batches are bounded to 100; each PK update preserves its own ID.
        for (Long id : ids.stream().distinct().sorted().toList()) {
            var filter = new MesMaterialIssueExample();
            filter.createCriteria().andIdEqualTo(id);
            var lock = new MesMaterialIssueEntity();
            lock.setId(id);
            if (issues.updateByExampleSelective(lock, filter) != 1) {
                throw new FoundationException(ErrorCode.NOT_FOUND);
            }
        }
    }

    public void deletingIssues(List<Long> ids) {
        lockIssues(ids);
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
    }
}
