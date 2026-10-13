package dev.mesbase.business.issue.service;

import dev.mesbase.business.basic.dao.MaterialMapper;
import dev.mesbase.business.basic.dao.UnitMeasureMapper;
import dev.mesbase.business.issue.dao.MesMaterialIssueMapper;
import dev.mesbase.business.issue.dao.MesMaterialIssueItemMapper;
import dev.mesbase.business.issue.domain.MesMaterialIssueEntity;
import dev.mesbase.business.issue.domain.MesMaterialIssueExample;
import dev.mesbase.business.issue.domain.MesMaterialIssueItemEntity;
import dev.mesbase.business.issue.domain.MesMaterialIssueItemExample;
import dev.mesbase.business.pro.dao.ProWorkorderMapper;
import dev.mesbase.business.production.dao.RoutingMapper;
import dev.mesbase.business.production.dao.RoutingProductMapper;
import dev.mesbase.business.production.domain.RoutingProductExample;
import dev.mesbase.business.wm.dao.WmWarehouseMapper;
import dev.mesbase.common.utils.SecurityUtils;
import dev.mesbase.core.error.ErrorCode;
import dev.mesbase.core.error.FoundationException;
import dev.mesbase.platform.execution.FactoryContext;
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
    private final ProWorkorderMapper workorders;
    private final RoutingMapper routings;
    private final RoutingProductMapper routingProducts;

    public MaterialIssueRules(MesMaterialIssueMapper issues, MesMaterialIssueItemMapper items,
                              MaterialMapper materials, UnitMeasureMapper units, WmWarehouseMapper warehouses,
                              ProWorkorderMapper workorders, RoutingMapper routings,
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

    private String factory() {
        return FactoryContext.required().factoryId().value();
    }

    private void require(boolean valid) {
        if (!valid) throw new FoundationException(ErrorCode.BAD_REQUEST);
    }

    public void validate(MesMaterialIssueEntity row) {
        require(row.getStatus() != null && Set.of("DRAFT", "RECORDED").contains(row.getStatus()));
        require(row.getIssueNo() != null && !row.getIssueNo().isBlank());
        if (row.getProductId() != null) {
            var product = materials.selectByPrimaryKey(row.getProductId());
            require(product != null && factory().equals(product.getFactoryId()));
            row.setProductCode(product.getCode());
        }
        if (row.getWorkorderId() != null) {
            // pro_workorder has no factory column: the fixed business database is its scope.
            var workorder = workorders.selectByPrimaryKey(row.getWorkorderId());
            require(workorder != null);
            require(row.getProductId() == null || Objects.equals(row.getProductCode(), workorder.getProductCode()));
            row.setWorkorderCode(workorder.getWorkorderCode());
            row.setProductCode(workorder.getProductCode());
        }
        if (row.getRoutingId() != null) {
            var routing = routings.selectByPrimaryKey(row.getRoutingId());
            require(routing != null && factory().equals(routing.getFactoryId())
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
        require(material != null && factory().equals(material.getFactoryId()));
        row.setMaterialCode(material.getCode());
        row.setUnitCode(null);
        if (row.getUnitId() != null) {
            var unit = units.selectByPrimaryKey(row.getUnitId());
            require(unit != null && factory().equals(unit.getFactoryId()));
            row.setUnitCode(unit.getCode());
        }
        row.setWarehouseCode(null);
        if (row.getWarehouseId() != null) {
            var warehouse = warehouses.selectByPrimaryKey(row.getWarehouseId());
            require(warehouse != null && factory().equals(warehouse.getFactoryId()));
            row.setWarehouseCode(warehouse.getCode());
        }
    }

    public void lockIssues(List<Long> ids) {
        var filter = new MesMaterialIssueExample();
        filter.createCriteria().andFactoryIdEqualTo(factory()).andIdIn(ids.stream().distinct().sorted().toList());
        // Generated same-value UPDATE serializes child creation with parent deletion.
        var lock = new MesMaterialIssueEntity();
        lock.setFactoryId(factory());
        if (issues.updateByExampleSelective(lock, filter) != ids.stream().distinct().count()) {
            throw new FoundationException(ErrorCode.NOT_FOUND);
        }
    }

    public void deletingIssues(List<Long> ids) {
        lockIssues(ids);
        var filter = new MesMaterialIssueItemExample();
        filter.createCriteria().andFactoryIdEqualTo(factory()).andIssueIdIn(ids);
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
