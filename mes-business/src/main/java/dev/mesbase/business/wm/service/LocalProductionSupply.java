package dev.mesbase.business.wm.service;

import dev.mesbase.business.basic.dao.MaterialMapper;
import dev.mesbase.business.basic.dao.UnitMeasureMapper;
import dev.mesbase.business.integration.production.ProductionSupply.Calculation;
import dev.mesbase.business.integration.production.ProductionSupply.Context;
import dev.mesbase.business.integration.production.ProductionSupply.Order;
import dev.mesbase.business.integration.production.ProductionSupply.Requirement;
import dev.mesbase.business.integration.production.ProductionSupply.Stock;
import dev.mesbase.business.integration.warehouse.WarehouseIntegration.Selection;
import dev.mesbase.business.production.service.WorkorderMaterialService;
import dev.mesbase.business.integration.production.ProductionSupply.Basis;
import dev.mesbase.business.production.dao.WorkorderMapper;
import dev.mesbase.business.production.domain.MesWorkorderMaterialEntity;
import dev.mesbase.business.production.domain.ProductionScanCode;
import dev.mesbase.business.production.domain.WorkOrderStatus;
import dev.mesbase.business.production.domain.WorkorderEntity;
import dev.mesbase.business.shared.enums.BusinessSource;
import dev.mesbase.business.shared.enums.EnableStatus;
import dev.mesbase.business.wm.dao.WmInventoryMapper;
import dev.mesbase.business.wm.dao.WmWarehouseMapper;
import dev.mesbase.business.wm.domain.WmInventoryExample;
import dev.mesbase.business.wm.domain.WmWarehouseExample;
import dev.mesbase.common.utils.StringUtils;
import dev.mesbase.common.validation.ParameterChecks;
import dev.mesbase.core.error.ErrorCode;
import dev.mesbase.core.error.FoundationException;
import dev.mesbase.core.factory.FactoryScope;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

@Service
@ConditionalOnProperty(name = "mes.business.enabled", havingValue = "true")
public class LocalProductionSupply {
    private final WorkorderMaterialService demands;
    private final MaterialMapper materials;
    private final UnitMeasureMapper units;
    private final WmInventoryMapper inventory;
    private final WmWarehouseMapper warehouses;
    private final WorkorderMapper orders;

    public LocalProductionSupply(MaterialMapper materials, UnitMeasureMapper units,
                                 WmInventoryMapper inventory, WmWarehouseMapper warehouses, WorkorderMapper orders,
                                 WorkorderMaterialService demands) {
        this.demands = demands;
        this.materials = materials;
        this.units = units;
        this.inventory = inventory;
        this.warehouses = warehouses;
        this.orders = orders;
    }

    public void validateRequestedSource(Long orderId, String reference) {
        Long id;
        try {
            id = Long.valueOf(reference);
        } catch (NumberFormatException e) {
            throw new FoundationException(ErrorCode.BAD_REQUEST, e);
        }
        var stock = inventory.selectByPrimaryKey(id);
        var order = orders.selectByPrimaryKey(orderId);
        var warehouse = Optional.ofNullable(stock)
                .map(nullableValue -> warehouses.selectByPrimaryKey(nullableValue.getWarehouseId())).orElse(null);
        if (order == null || warehouse == null || !BusinessSource.LOCAL.getValue().equals(order.getSource())
                || !BusinessSource.LOCAL.getValue().equals(warehouse.getSource()))
            throw new FoundationException(ErrorCode.BUSINESS_SOURCE_CONFLICT);
    }

    public Context query(ProductionScanCode identity, WorkorderEntity order, FactoryScope scope,
                         Selection selection, int offset, int limit) {
        if (!BusinessSource.LOCAL.getValue().equals(order.getSource()))
            throw new FoundationException(ErrorCode.BUSINESS_SOURCE_CONFLICT);
        if (!WorkOrderStatus.RELEASED.getValue().equals(order.getStatus()))
            return new Context(selection.mode().getValue(), List.of(), List.of(), 0, offset, limit);
        var demand = demands.basis(order);
        var materialId = demand.getMaterialId();
        var total = demand.getTotalRequiredQty();
        var center = demand.getWorkCenter();
        var allowed = warehouseIds(demand);
        if (total == null || total.signum() <= 0 || StringUtils.isBlank(center) || allowed.isEmpty())
            ParameterChecks.reject(ErrorCode.BAD_REQUEST);
        var material = materials.selectByPrimaryKey(materialId);
        if (material == null || !EnableStatus.ENABLED.getValue().equals(material.getStatus()))
            ParameterChecks.reject(ErrorCode.BAD_REQUEST);
        var unit = Optional.ofNullable(material.getUnitId()).map(units::selectByPrimaryKey).orElse(null);
        if (unit == null || !EnableStatus.ENABLED.getValue().equals(unit.getStatus())
                || demand != null && !Objects.equals(demand.getUnitId(), unit.getId()))
            ParameterChecks.reject(ErrorCode.BAD_REQUEST);
        var product = materials.selectByPrimaryKey(order.getProductId());
        if (product == null || !identity.workorderCode().equals(order.getCode())
                || !identity.productCode().equals(material.getCode()))
            ParameterChecks.reject(ErrorCode.BAD_REQUEST);
        var outputUnit = Optional.ofNullable(product.getUnitId()).map(units::selectByPrimaryKey).orElse(null);
        if (outputUnit == null || !Objects.equals(outputUnit.getId(), demand.getOutputUnitId()) || !EnableStatus.ENABLED.getValue()
                .equals(outputUnit.getStatus()))
            ParameterChecks.reject(ErrorCode.BAD_REQUEST);
        var sf = new WmInventoryExample();
        sf.createCriteria().andMaterialIdEqualTo(material.getId()).andBatchNoEqualTo(identity.batchNo())
                .andWarehouseIdIn(allowed).andStatusEqualTo(EnableStatus.ENABLED.getValue());
        if (inventory.countByExample(sf) > 100) ParameterChecks.reject(ErrorCode.BAD_REQUEST);
        sf.setOrderByClause("id DESC");
        var stockRows = inventory.selectByExample(sf);
        var wf = new WmWarehouseExample();
        wf.createCriteria().andIdIn(allowed);
        var warehouseMap = warehouses.selectByExample(wf).stream().collect(Collectors.toMap(w -> w.getId(), w -> w));
        var now = LocalDateTime.now();
        var stocks = new ArrayList<Stock>();
        for (var stock : stockRows) {
            if (!identity.batchNo().equals(stock.getBatchNo())) continue;
            var warehouse = warehouseMap.get(stock.getWarehouseId());
            var available = stock.getAvailableQuantity() == null ? stock.getQuantity()
                    : stock.getAvailableQuantity().min(stock.getQuantity());
            if (warehouse == null || !BusinessSource.LOCAL.getValue().equals(warehouse.getSource())
                    || !EnableStatus.ENABLED.getValue().equals(warehouse.getStatus()) || available.signum() <= 0)
                continue;
            stocks.add(new Stock(stock.getId().toString(), material.getId(), material.getCode(), stock.getBatchNo(),
                    warehouse.getId(), warehouse.getCode(), stock.getBinCode(), unit.getId(), unit.getCode(), available, now));
        }
        var candidate = new Order(order.getId(), order.getCode(), product.getCode(), product.getName(), center,
                order.getPlannedQty(), outputUnit.getCode(), order.getStatus(), allowed,
                List.of(new Requirement(material.getCode(), material.getId(), unit.getId(), unit.getCode(), total)),
                "LOCAL-WORKORDER:" + order.getId() + ":R" + demand.getRevision(), now, product.getId());
        // A production code identifies one work order; paging never broadens it to unrelated orders.
        var page = offset == 0 ? List.of(candidate) : List.<Order>of();
        return new Context(selection.mode().getValue(), List.copyOf(stocks), page, 1, offset, limit);
    }

    public void validateFrozenCalculation(Calculation calculation) {
        var row = demands.basis(orders.selectByPrimaryKey(calculation.order().workorderId()));
        String version = "LOCAL-WORKORDER:" + row.getId() + ":R" + row.getRevision();
        if (!version.equals(calculation.order().sourceReference())
                || row.getPlannedQty().compareTo(calculation.order().plannedQty()) != 0
                || row.getTotalRequiredQty()
                .compareTo(calculation.order().requirements().getFirst().totalRequiredQty()) != 0)
            throw new FoundationException(ErrorCode.PRODUCTION_INPUT_STALE);
        demands.freeze(calculation);
    }

    public Calculation calculate(String sourceMode, Order candidate, Stock stock, BigDecimal outputQty) {
        var order = orders.selectByPrimaryKey(candidate.workorderId());
        var row = demands.basis(order);
        var amount = demands.issueQuantity(order, outputQty);
        return new Calculation(sourceMode, candidate, stock, outputQty, amount,
                new Basis(row.getBomId(), row.getBomBaseQty(), row.getBomComponentQty(), row.getOutputUnitId()));
    }

    private static List<Long> warehouseIds(MesWorkorderMaterialEntity row) {
        return Arrays.stream(new ObjectMapper().readValue(row.getWarehouseIds(), Long[].class)).toList();
    }

}
