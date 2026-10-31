package dev.mesbase.business.production.service;

import dev.mesbase.business.basic.dao.MaterialMapper;
import dev.mesbase.business.basic.dao.UnitMeasureMapper;
import dev.mesbase.business.basic.domain.MaterialEntity;
import dev.mesbase.business.basic.domain.MaterialExample;
import dev.mesbase.business.basic.domain.UnitMeasureEntity;
import dev.mesbase.business.basic.domain.UnitMeasureExample;
import dev.mesbase.business.directory.domain.DirectoryLabel;
import dev.mesbase.business.production.dao.MesProductionCombinationItemMapper;
import dev.mesbase.business.production.dao.MesProductionCombinationMapper;
import dev.mesbase.business.production.dao.MesProductionExecutionMapper;
import dev.mesbase.business.production.dao.ProductionInputExtraMapper;
import dev.mesbase.business.production.dao.WorkOrderExtraMapper;
import dev.mesbase.business.production.dao.WorkorderMapper;
import dev.mesbase.business.production.domain.MesProductionCombinationEntity;
import dev.mesbase.business.production.domain.MesProductionCombinationExample;
import dev.mesbase.business.production.domain.MesProductionCombinationItemEntity;
import dev.mesbase.business.production.domain.MesProductionCombinationItemExample;
import dev.mesbase.business.production.domain.ProductionCombinationModels.Allocation;
import dev.mesbase.business.production.domain.ProductionCombinationModels.Calculation;
import dev.mesbase.business.production.domain.ProductionCombinationModels.Demand;
import dev.mesbase.business.production.domain.ProductionCombinationModels.Detail;
import dev.mesbase.business.production.domain.ProductionCombinationModels.Generated;
import dev.mesbase.business.production.domain.ProductionCombinationModels.Item;
import dev.mesbase.business.production.domain.ProductionCombinationModels.Selection;
import dev.mesbase.business.production.domain.ProductionCombinationModels.Stock;
import dev.mesbase.business.production.domain.ProductionCombinationPlanner;
import dev.mesbase.business.production.domain.ProductionCombinationRules;
import dev.mesbase.business.production.domain.ProductionCombinationStockChangedException;
import dev.mesbase.business.production.domain.ProductionExecutionStatus;
import dev.mesbase.business.production.domain.WorkOrderStatus;
import dev.mesbase.business.production.domain.WorkorderEntity;
import dev.mesbase.business.shared.enums.BusinessSource;
import dev.mesbase.business.shared.enums.EnableStatus;
import dev.mesbase.business.wm.dao.WmInventoryMapper;
import dev.mesbase.business.wm.dao.WmWarehouseMapper;
import dev.mesbase.business.wm.domain.WmInventoryExample;
import dev.mesbase.business.wm.domain.WmWarehouseExample;
import dev.mesbase.common.utils.RequestFingerprint;
import dev.mesbase.common.utils.SecurityUtils;
import dev.mesbase.common.validation.ParameterChecks;
import dev.mesbase.core.error.ErrorCode;
import dev.mesbase.core.error.FoundationException;
import dev.mesbase.platform.execution.FactoryContext;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Service
@ConditionalOnProperty(name = "mes.business.enabled", havingValue = "true")
@Transactional(transactionManager = "businessTransactionManager", isolation = Isolation.READ_COMMITTED, rollbackFor = Exception.class)
public class ProductionCombinationService {
    private final MesProductionCombinationMapper combinations;
    private final MesProductionCombinationItemMapper items;
    private final WorkorderMapper orders;
    private final MesProductionExecutionMapper executions;
    private final WorkorderMaterialService demands;
    private final MaterialMapper materials;
    private final UnitMeasureMapper units;
    private final WmInventoryMapper inventory;
    private final WmWarehouseMapper warehouses;
    private final ProductionInputExtraMapper inputs;
    private final WorkOrderExtraMapper allocations;
    private final ProductionChainLocks locks;
    private final BusinessSourceRules sources;
    private final MaterialQuantityRules quantities;

    public ProductionCombinationService(MesProductionCombinationMapper combinations,
                                        MesProductionCombinationItemMapper items,
                                        WorkorderMapper orders, MesProductionExecutionMapper executions,
                                        WorkorderMaterialService demands,
                                        MaterialMapper materials, UnitMeasureMapper units, WmInventoryMapper inventory,
                                        WmWarehouseMapper warehouses,
                                        ProductionInputExtraMapper inputs, WorkOrderExtraMapper allocations,
                                        ProductionChainLocks locks,
                                        BusinessSourceRules sources, MaterialQuantityRules quantities) {
        this.combinations = combinations;
        this.items = items;
        this.orders = orders;
        this.executions = executions;
        this.demands = demands;
        this.materials = materials;
        this.units = units;
        this.inventory = inventory;
        this.warehouses = warehouses;
        this.inputs = inputs;
        this.allocations = allocations;
        this.locks = locks;
        this.sources = sources;
        this.quantities = quantities;
    }

    public record Context(WorkorderEntity order, List<Demand> demands, List<Stock> stocks, BigDecimal confirmed,
                          Map<Long, BigDecimal> issued, BigDecimal remaining, int outputPrecision,
                          Map<Long, DirectoryLabel> warehouseLabels) {
    }

    public Generated generate(Long workorderId, BigDecimal requested) {
        var order = locks.order(workorderId);
        sources.localCreation();
        sources.order(workorderId);
        if (!List.of(WorkOrderStatus.DRAFT.getValue(), WorkOrderStatus.RELEASED.getValue()).contains(order.getStatus()))
            ParameterChecks.reject(ErrorCode.WORKORDER_NOT_RELEASED);
        quantities.checkForMaterial(requested, order.getProductId());
        if (requested.signum() <= 0) ParameterChecks.reject(ErrorCode.BAD_REQUEST);
        var context = context(order, null);
        var plan = ProductionCombinationPlanner.generate(context.demands(), context.stocks(), order.getPlannedQty(),
                context.confirmed(), context.issued(), requested, context.remaining(), context.outputPrecision());
        if (plan.outputQty().signum() <= 0) ParameterChecks.reject(ErrorCode.STOCK_INSUFFICIENT);
        String hash = ProductionCombinationRules.fingerprint(order.getId(), context.demands(), plan.selections());
        var filter = new MesProductionCombinationExample();
        filter.createCriteria().andWorkorderIdEqualTo(order.getId())
                .andDemandRevisionEqualTo(order.getMaterialRevision()).andFingerprintEqualTo(hash);
        var found = combinations.selectByExample(filter);
        if (found.size() > 1) ParameterChecks.reject(ErrorCode.RESOURCE_CONFLICT);
        MesProductionCombinationEntity header;
        if (!found.isEmpty()) header = found.getFirst();
        else {
            header = new MesProductionCombinationEntity();
            header.setWorkorderId(order.getId());
            header.setProductId(order.getProductId());
            header.setDemandRevision(order.getMaterialRevision());
            header.setPlannedQty(order.getPlannedQty());
            header.setOutputUnitId(materials.selectByPrimaryKey(order.getProductId()).getUnitId());
            header.setFingerprint(hash);
            header.setCreatedBy(SecurityUtils.getUserId());
            header.setCreatedAt(LocalDateTime.now());
            if (combinations.insert(header) != 1) ParameterChecks.reject(ErrorCode.RESOURCE_CONFLICT);
            var byId = context.demands().stream().collect(Collectors.toMap(Demand::id, Function.identity()));
            for (var selection : plan.selections()) {
                var demand = byId.get(selection.demandLineId());
                for (String batch : selection.batches()) {
                    var item = new MesProductionCombinationItemEntity();
                    item.setCombinationId(header.getId());
                    item.setDemandLineId(demand.id());
                    item.setLineNo(demand.lineNo());
                    item.setMaterialId(demand.materialId());
                    item.setMaterialCode(demand.materialCode());
                    item.setUnitId(demand.unitId());
                    item.setRequiredQty(demand.totalRequiredQty());
                    item.setBatchNo(batch);
                    if (items.insert(item) != 1) ParameterChecks.reject(ErrorCode.RESOURCE_CONFLICT);
                }
            }
        }
        return new Generated(detail(header.getId()), requested, plan.outputQty(), plan.allocations(), "MAX_OUTPUT_MIN_COMBINATIONS_MIN_MATERIAL_LOTS_DETERMINISTIC_POSITIONS");
    }

    @Transactional(transactionManager = "businessTransactionManager", readOnly = true)
    public Detail detail(Long id) {
        var header = require(id);
        var order = orders.selectByPrimaryKey(header.getWorkorderId());
        if (order == null) ParameterChecks.reject(ErrorCode.NOT_FOUND);
        var grouped = new LinkedHashMap<Long, List<MesProductionCombinationItemEntity>>();
        for (var row : rows(id)) grouped.computeIfAbsent(row.getDemandLineId(), k -> new ArrayList<>()).add(row);
        var view = grouped.values().stream().map(values -> {
            var first = values.getFirst();
            return new Item(first.getDemandLineId(), first.getLineNo(), first.getMaterialId(), first.getMaterialCode(),
                    first.getUnitId(), first.getRequiredQty(), values.stream()
                    .map(MesProductionCombinationItemEntity::getBatchNo)
                    .distinct().sorted(ProductionCombinationRules.BATCH_ORDER).toList());
        }).toList();
        String status = null;
        boolean closed = false;
        if (header.getExecutionId() != null) {
            var execution = executions.selectByPrimaryKey(header.getExecutionId());
            if (execution == null) ParameterChecks.reject(ErrorCode.RESOURCE_CONFLICT);
            status = execution.getStatus();
            closed = ended(status);
        }
        return new Detail(id, ProductionCombinationRules.code(FactoryContext.required().factoryId()
                .value(), id), order.getId(), order.getCode(), header.getProductId(),
                header.getOutputUnitId(), header.getDemandRevision(), header.getExecutionId(), status, closed, view);
    }

    public MesProductionCombinationEntity require(Long id) {
        var row = combinations.selectByPrimaryKey(id);
        if (row == null) ParameterChecks.reject(ErrorCode.NOT_FOUND);
        return row;
    }

    private List<MesProductionCombinationItemEntity> rows(Long id) {
        var filter = new MesProductionCombinationItemExample();
        filter.createCriteria().andCombinationIdEqualTo(id);
        filter.setOrderByClause("line_no ASC,demand_line_id ASC,id ASC");
        if (items.countByExample(filter) > 10000) ParameterChecks.reject(ErrorCode.BAD_REQUEST);
        return items.selectByExample(filter);
    }

    public static boolean ended(String status) {
        return ProductionExecutionStatus.FULLY_RECEIVED.getValue().equals(status)
                || ProductionExecutionStatus.CANCELLED.getValue().equals(status);
    }

    @Transactional(transactionManager = "businessTransactionManager", readOnly = true)
    public Calculation calculate(String code, BigDecimal requested) {
        var header = require(ProductionCombinationRules.parse(code, FactoryContext.required().factoryId().value()));
        var order = orders.selectByPrimaryKey(header.getWorkorderId());
        if (order == null) ParameterChecks.reject(ErrorCode.NOT_FOUND);
        return calculate(header, order, requested);
    }

    public Calculation calculate(MesProductionCombinationEntity header, WorkorderEntity order, BigDecimal requested) {
        var view = detail(header.getId());
        if (view.closed()) ParameterChecks.reject(ErrorCode.PRODUCTION_COMBINATION_CLOSED);
        sources.localCreation();
        sources.order(order.getId());
        if (!WorkOrderStatus.RELEASED.getValue().equals(order.getStatus()))
            ParameterChecks.reject(ErrorCode.WORKORDER_NOT_RELEASED);
        if (!Objects.equals(header.getDemandRevision(), order.getMaterialRevision())
                || !Objects.equals(header.getProductId(), order.getProductId())
                || header.getPlannedQty().compareTo(order.getPlannedQty()) != 0)
            ParameterChecks.reject(ErrorCode.PRODUCTION_INPUT_STALE);
        var context = context(order, header.getExecutionId());
        var selected = view.items().stream().map(i -> new Selection(i.demandLineId(), i.batches())).toList();
        if (!ProductionCombinationRules.fingerprint(order.getId(), context.demands(), selected)
                .equals(header.getFingerprint()))
            ParameterChecks.reject(ErrorCode.PRODUCTION_INPUT_STALE);
        var maximum = ProductionCombinationPlanner.fixed(context.demands(), context.stocks(), selected, order.getPlannedQty(),
                        context.confirmed(), context.issued(), null, context.remaining(), context.outputPrecision())
                .outputQty();
        var quantity = requested;
        if (quantity == null) quantity = maximum;
        if (quantity.signum() < 0) ParameterChecks.reject(ErrorCode.BAD_REQUEST);
        List<Allocation> allocation = List.of();
        if (quantity.signum() > 0) {
            quantities.checkForMaterial(quantity, order.getProductId());
            if (quantity.compareTo(maximum) > 0) throw new ProductionCombinationStockChangedException(maximum);
            allocation = ProductionCombinationPlanner.fixed(context.demands(), context.stocks(), selected, order.getPlannedQty(),
                            context.confirmed(), context.issued(), quantity, context.remaining(), context.outputPrecision())
                    .allocations();
        }
        record Key(Long id, Long revision, String output) {
        }
        String key = RequestFingerprint.sha256(new ObjectMapper().writeValueAsString(new Key(header.getId(), header.getDemandRevision(),
                quantity.stripTrailingZeros().toPlainString())));
        return new Calculation(view, maximum, quantity, allocation, key, allocation.stream()
                .map(Allocation::warehouseId)
                .distinct().sorted().map(context.warehouseLabels()::get).toList());
    }

    public void bind(Long combinationId, Long executionId) {
        var header = require(combinationId);
        if (header.getExecutionId() != null && !header.getExecutionId().equals(executionId))
            ParameterChecks.reject(ErrorCode.RESOURCE_CONFLICT);
        header.setExecutionId(executionId);
        if (combinations.updateByPrimaryKey(header) != 1) ParameterChecks.reject(ErrorCode.RESOURCE_CONFLICT);
    }

    private Context context(WorkorderEntity order, Long executionId) {
        if (!EnableStatus.ENABLED.getValue().equals(order.getMaterialStatus()))
            ParameterChecks.reject(ErrorCode.PRODUCTION_INPUT_STALE);
        var rows = demands.lines(order.getId());
        if (rows.isEmpty() || rows.size() > 100) ParameterChecks.reject(ErrorCode.PRODUCTION_INPUT_STALE);
        var materialIds = new ArrayList<>(rows.stream().map(r -> r.getMaterialId()).distinct().toList());
        materialIds.add(order.getProductId());
        var mf = new MaterialExample();
        mf.createCriteria().andIdIn(materialIds);
        var materialMap = materials.selectByExample(mf).stream()
                .collect(Collectors.toMap(MaterialEntity::getId, Function.identity()));
        var unitIds = materialMap.values().stream().map(MaterialEntity::getUnitId).filter(Objects::nonNull).distinct()
                .toList();
        if (unitIds.isEmpty()) ParameterChecks.reject(ErrorCode.PRODUCTION_INPUT_STALE);
        var uf = new UnitMeasureExample();
        uf.createCriteria().andIdIn(unitIds);
        var unitMap = units.selectByExample(uf).stream()
                .collect(Collectors.toMap(UnitMeasureEntity::getId, Function.identity()));
        var output = materialMap.get(order.getProductId());
        if (output == null) ParameterChecks.reject(ErrorCode.PRODUCTION_INPUT_STALE);
        int outputPrecision = precision(unitMap.get(output.getUnitId()));
        var demandRows = new ArrayList<Demand>();
        for (var row : rows) {
            var material = materialMap.get(row.getMaterialId());
            if (material == null || !EnableStatus.ENABLED.getValue().equals(material.getStatus())
                    || !Objects.equals(material.getUnitId(), row.getUnitId()) || row.getTotalRequiredQty() == null
                    || row.getTotalRequiredQty().signum() <= 0 || row.getPlannedQty()
                    .compareTo(order.getPlannedQty()) != 0)
                ParameterChecks.reject(ErrorCode.PRODUCTION_INPUT_STALE);
            demandRows.add(new Demand(row.getId(), row.getLineNo(), material.getId(), material.getCode(), material.getUnitId(),
                    row.getTotalRequiredQty(), precision(unitMap.get(material.getUnitId()))));
        }
        var warehouseFilter = new WmWarehouseExample();
        var wc = warehouseFilter.createCriteria().andSourceEqualTo(BusinessSource.LOCAL.getValue())
                .andStatusEqualTo(EnableStatus.ENABLED.getValue());
        if (order.getMaterialWarehouseIds() != null && !order.getMaterialWarehouseIds().isBlank()) {
            var allowed = Arrays.asList(new ObjectMapper().readValue(order.getMaterialWarehouseIds(), Long[].class));
            if (allowed.isEmpty() || allowed.size() > 100) ParameterChecks.reject(ErrorCode.PRODUCTION_INPUT_STALE);
            sources.warehouses(order.getSource(), allowed);
            wc.andIdIn(allowed);
        }
        var warehouseRows = warehouses.selectByExample(warehouseFilter);
        var warehouseLabels = warehouseRows.stream().collect(Collectors.toMap(w -> w.getId(),
                w -> new DirectoryLabel(w.getId(), w.getCode(), w.getName(), null)));
        var warehouseIds = warehouseRows.stream().map(w -> w.getId()).toList();
        var stockRows = new ArrayList<Stock>();
        if (!warehouseIds.isEmpty()) {
            var sf = new WmInventoryExample();
            sf.createCriteria().andMaterialIdIn(materialIds).andWarehouseIdIn(warehouseIds)
                    .andStatusEqualTo(EnableStatus.ENABLED.getValue());
            sf.setOrderByClause("id ASC");
            if (inventory.countByExample(sf) > 2000) ParameterChecks.reject(ErrorCode.PRODUCTION_COMBINATION_BUDGET);
            for (var stock : inventory.selectByExample(sf)) {
                if (stock.getQuantity() == null || stock.getQuantity().signum() < 0)
                    ParameterChecks.reject(ErrorCode.RESOURCE_CONFLICT);
                var available = stock.getQuantity();
                if (stock.getAvailableQuantity() != null) available = stock.getAvailableQuantity();
                if (available.compareTo(stock.getQuantity()) > 0) ParameterChecks.reject(ErrorCode.RESOURCE_CONFLICT);
                if (available.signum() < 0) ParameterChecks.reject(ErrorCode.RESOURCE_CONFLICT);
                var material = materialMap.get(stock.getMaterialId());
                if (material != null)
                    stockRows.add(new Stock(stock.getId(), stock.getMaterialId(), stock.getBatchNo(), stock.getWarehouseId(),
                            stock.getBinCode(), material.getUnitId(), available));
            }
        }
        if (inputs.unlinkedDemandLines(order.getId()) > 0) ParameterChecks.reject(ErrorCode.PRODUCTION_INPUT_STALE);
        var issued = new HashMap<Long, BigDecimal>();
        for (var item : inputs.issuedByDemand(order.getId())) {
            if (item.demandLineId() == null) ParameterChecks.reject(ErrorCode.PRODUCTION_INPUT_STALE);
            issued.put(item.demandLineId(), item.quantity());
        }
        var capacity = allocations.activeAllocation(order.getId(), executionId);
        if (capacity == null) ParameterChecks.reject(ErrorCode.RESOURCE_CONFLICT);
        var remaining = capacity.remainingTargetQty();
        if (executionId != null) {
            var execution = executions.selectByPrimaryKey(executionId);
            if (execution == null || !order.getId().equals(execution.getWorkorderId()) || ended(execution.getStatus()))
                ParameterChecks.reject(ErrorCode.PRODUCTION_COMBINATION_CLOSED);
            // The allocation query excludes this execution; only its already-confirmed contribution
            // consumes its remaining capacity. Other executions keep their original target allocation.
            remaining = remaining.subtract(inputs.confirmedExecutionOutput(executionId));
        }
        return new Context(order, List.copyOf(demandRows), List.copyOf(stockRows), inputs.confirmedOutput(order.getId()),
                Map.copyOf(issued), remaining.max(BigDecimal.ZERO), outputPrecision, Map.copyOf(warehouseLabels));
    }

    private static int precision(UnitMeasureEntity unit) {
        if (unit == null || !EnableStatus.ENABLED.getValue()
                .equals(unit.getStatus()) || unit.getMustBeWholeNumber() == null
                || unit.getMustBeWholeNumber() != 0 && unit.getMustBeWholeNumber() != 1
                || unit.getQuantityPrecision() == null || unit.getQuantityPrecision() < 0 || unit.getQuantityPrecision() > 6)
            throw new FoundationException(ErrorCode.PRODUCTION_INPUT_STALE);
        if (unit.getMustBeWholeNumber() == 1) return 0;
        return unit.getQuantityPrecision();
    }
}
