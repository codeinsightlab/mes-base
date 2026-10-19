package dev.mesbase.business.basic.service;

import dev.mesbase.business.basic.dao.MaterialMapper;
import dev.mesbase.business.basic.dao.UnitMeasureMapper;
import dev.mesbase.business.basic.domain.MaterialEntity;
import dev.mesbase.business.basic.domain.MaterialExample;
import dev.mesbase.business.issue.dao.MesMaterialIssueItemMapper;
import dev.mesbase.business.issue.domain.MesMaterialIssueItemExample;
import dev.mesbase.business.production.dao.MesWorkorderMaterialMapper;
import dev.mesbase.business.production.domain.MesWorkorderMaterialExample;
import dev.mesbase.business.production.dao.RoutingProductMapper;
import dev.mesbase.business.production.domain.RoutingProductExample;
import dev.mesbase.business.production.dao.WorkorderMapper;
import dev.mesbase.business.production.dao.MesProductionExecutionMapper;
import dev.mesbase.business.production.domain.MesProductionExecutionExample;
import dev.mesbase.business.production.domain.WorkorderExample;
import dev.mesbase.business.wm.dao.WmInventoryMapper;
import dev.mesbase.business.wm.dao.WmStockActionMapper;
import dev.mesbase.business.wm.dao.WmStockDocumentLineMapper;
import dev.mesbase.business.wm.domain.WmInventoryExample;
import dev.mesbase.business.wm.domain.WmStockActionExample;
import dev.mesbase.business.wm.domain.WmStockDocumentLineExample;
import dev.mesbase.core.error.ErrorCode;
import dev.mesbase.core.error.FoundationException;
import java.util.List;
import java.util.Objects;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Metering identity guard; all checks use the current Factory's business database.
 */
@Component
@ConditionalOnProperty(name = "mes.business.enabled", havingValue = "true")
public class MaterialUnitRules {
    private final MaterialMapper materials;
    private final UnitMeasureMapper units;
    private final WmInventoryMapper stocks;
    private final WmStockActionMapper actions;
    private final WmStockDocumentLineMapper documents;
    private final MesWorkorderMaterialMapper demands;
    private final MesMaterialIssueItemMapper issues;
    private final RoutingProductMapper products;
    private final WorkorderMapper orders;
    private final MesProductionExecutionMapper executions;

    public MaterialUnitRules(MaterialMapper materials, UnitMeasureMapper units, WmInventoryMapper stocks,
                             WmStockActionMapper actions, WmStockDocumentLineMapper documents,
                             MesWorkorderMaterialMapper demands, MesMaterialIssueItemMapper issues,
                             RoutingProductMapper products, WorkorderMapper orders,
                             MesProductionExecutionMapper executions) {
        this.materials = materials;
        this.units = units;
        this.stocks = stocks;
        this.actions = actions;
        this.documents = documents;
        this.demands = demands;
        this.issues = issues;
        this.products = products;
        this.orders = orders;
        this.executions = executions;
    }

    public MaterialEntity lockMaterial(Long id) {
        var row = materials.selectByPrimaryKeyForUpdate(id);
        if (row == null) missing();
        return row;
    }

    /**
     * Locking reads share the existing reference-creation row-lock protocol.
     */
    public void changingUnit(MaterialEntity before, Long next) {
        if (Objects.equals(before.getUnitId(), next)) return;
        Long id = before.getId();
        var stock = new WmInventoryExample();
        stock.createCriteria().andMaterialIdEqualTo(id);
        var action = new WmStockActionExample();
        action.createCriteria().andMaterialIdEqualTo(id);
        var document = new WmStockDocumentLineExample();
        document.createCriteria().andMaterialIdEqualTo(id);
        var demand = new MesWorkorderMaterialExample();
        demand.createCriteria().andMaterialIdEqualTo(id);
        var issue = new MesMaterialIssueItemExample();
        issue.createCriteria().andMaterialIdEqualTo(id);
        var product = new RoutingProductExample();
        product.createCriteria().andMaterialIdEqualTo(id);
        var order = new WorkorderExample();
        order.createCriteria().andProductIdEqualTo(id);
        var execution = new MesProductionExecutionExample();
        execution.createCriteria().andProductIdEqualTo(id);
        if (stocks.countByExample(stock) > 0 || actions.countByExample(action) > 0
                || documents.countByExample(document) > 0 || demands.countByExample(demand) > 0
                || issues.countByExample(issue) > 0 || products.countByExample(product) > 0
                || orders.countByExample(order) > 0 || executions.countByExample(execution) > 0) conflict();
    }

    /**
     * Lock order is material -> unit. Unit deletion locks only units and never waits for material locks.
     */
    public void lockUnit(Long id) {
        if (id == null) missing();
        if (units.selectByPrimaryKeyForUpdate(id) == null) missing();
    }

    public void deletingUnits(List<Long> ids) {
        ids.stream().sorted().forEach(this::lockUnit);
        var material = new MaterialExample();
        material.createCriteria().andUnitIdIn(ids);
        var action = new WmStockActionExample();
        action.createCriteria().andUnitIdIn(ids);
        var document = new WmStockDocumentLineExample();
        document.createCriteria().andUnitIdIn(ids);
        var demand = new MesWorkorderMaterialExample();
        demand.createCriteria().andUnitIdIn(ids);
        var issue = new MesMaterialIssueItemExample();
        issue.createCriteria().andUnitIdIn(ids);
        if (materials.countByExample(material) > 0 || actions.countByExample(action) > 0
                || documents.countByExample(document) > 0 || demands.countByExample(demand) > 0
                || issues.countByExample(issue) > 0) conflict();
    }

    private void missing() {
        throw new FoundationException(ErrorCode.NOT_FOUND);
    }

    private void conflict() {
        throw new FoundationException(ErrorCode.RESOURCE_CONFLICT);
    }
}
