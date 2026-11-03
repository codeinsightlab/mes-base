package dev.mesbase.business.production.service;

import dev.mesbase.business.basic.dao.MaterialMapper;
import dev.mesbase.business.basic.dao.UnitMeasureMapper;
import dev.mesbase.business.basic.domain.MaterialEntity;
import dev.mesbase.business.basic.domain.MaterialExample;
import dev.mesbase.business.basic.domain.UnitMeasureEntity;
import dev.mesbase.business.basic.domain.UnitMeasureExample;
import dev.mesbase.business.directory.domain.DirectoryLabel;
import dev.mesbase.business.production.dao.MesProductionExecutionMapper;
import dev.mesbase.business.production.dao.ProductionInputExtraMapper;
import dev.mesbase.business.production.dao.WorkOrderExtraMapper;
import dev.mesbase.business.production.dao.WorkorderMapper;
import dev.mesbase.business.production.domain.ProductionCombinationModels;
import dev.mesbase.business.production.domain.ProductionCombinationModels.Allocation;
import dev.mesbase.business.production.domain.ProductionCombinationModels.Calculation;
import dev.mesbase.business.production.domain.ProductionCombinationModels.Demand;
import dev.mesbase.business.production.domain.ProductionCombinationModels.Detail;
import dev.mesbase.business.production.domain.ProductionCombinationModels.Generated;
import dev.mesbase.business.production.domain.ProductionCombinationModels.Item;
import dev.mesbase.business.production.domain.ProductionCombinationModels.Selection;
import dev.mesbase.business.production.domain.ProductionCombinationModels.Stock;
import dev.mesbase.business.production.domain.ProductionCombinationPlanner;
import dev.mesbase.business.production.domain.ProductionQrCode;
import dev.mesbase.business.production.domain.WorkorderExample;
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
import dev.mesbase.core.error.SafeBusinessMessage;
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
    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(ProductionCombinationService.class);
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

    public ProductionCombinationService(
            WorkorderMapper orders, MesProductionExecutionMapper executions,
            WorkorderMaterialService demands,
            MaterialMapper materials, UnitMeasureMapper units, WmInventoryMapper inventory,
            WmWarehouseMapper warehouses,
            ProductionInputExtraMapper inputs, WorkOrderExtraMapper allocations,
            ProductionChainLocks locks,
            BusinessSourceRules sources, MaterialQuantityRules quantities) {
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
        if (requested.signum() <= 0)
            ParameterChecks.reject(ErrorCode.BAD_REQUEST, new SafeBusinessMessage("规划产出数量必须大于零"));
        var context = context(order, null);
        ProductionCombinationModels.Plan plan;
        try {
            plan = ProductionCombinationPlanner.generate(context.demands(), context.stocks(), order.getPlannedQty(),
                    context.confirmed(), context.issued(), requested, context.remaining(), context.outputPrecision());
        } catch (FoundationException failure) {
            if (failure.code() == ErrorCode.PRODUCTION_COMBINATION_SPLIT_BATCH_REQUIRED)
                LOG.error("event=production.qr.split_batch_rejected factory={} workorder={} requestedQty={} action=replenish_or_merge_batches",
                        FactoryContext.required().factoryId().value(), order.getCode(), requested);
            throw failure;
        }
        if (plan.outputQty().signum() <= 0) ParameterChecks.reject(ErrorCode.STOCK_INSUFFICIENT);
        var byId = context.demands().stream().collect(Collectors.toMap(Demand::id, Function.identity()));
        var pairs = new ArrayList<ProductionQrCode.MaterialBatch>();
        for (var selection : plan.selections()) {
            var demand = byId.get(selection.demandLineId());
            for (String batch : selection.batches())
                pairs.add(new ProductionQrCode.MaterialBatch(demand.materialCode(), batch));
        }
        String code = ProductionQrCode.generate(order.getCode(), pairs);
        return new Generated(detail(code), requested, plan.outputQty(), plan.allocations(),
                "MAX_OUTPUT_MIN_COMBINATIONS_MIN_MATERIAL_LOTS_DETERMINISTIC_POSITIONS");
    }

    public WorkorderEntity order(String code) {
        var content = ProductionQrCode.parse(code);
        var filter = new WorkorderExample();
        filter.createCriteria().andCodeEqualTo(content.workorderCode());
        var found = orders.selectByExample(filter).stream()
                .filter(row -> content.workorderCode().equals(row.getCode())).toList();
        if (found.size() != 1) ParameterChecks.reject(ErrorCode.EXECUTION_QR_INVALID);
        return found.getFirst();
    }

    @Transactional(transactionManager = "businessTransactionManager", readOnly = true)
    public Detail detail(String code) {
        var content = ProductionQrCode.parse(code);
        String normalized = ProductionQrCode.generate(content.workorderCode(), content.materials());
        var order = order(normalized);
        var materialRows = demands.lines(order.getId());
        if (materialRows.isEmpty() || materialRows.size() > 100)
            ParameterChecks.reject(ErrorCode.PRODUCTION_INPUT_STALE);
        var filter = new MaterialExample();
        filter.createCriteria().andIdIn(materialRows.stream().map(r -> r.getMaterialId()).distinct().toList());
        var materialMap = materials.selectByExample(filter).stream()
                .collect(Collectors.toMap(MaterialEntity::getId, Function.identity()));
        var selected = new LinkedHashMap<String, List<String>>();
        for (var pair : content.materials())
            selected.computeIfAbsent(pair.materialCode(), key -> new ArrayList<>()).add(pair.batchNo());
        var view = new ArrayList<Item>();
        var seen = new java.util.HashSet<String>();
        for (var row : materialRows) {
            var material = materialMap.get(row.getMaterialId());
            if (material == null || !seen.add(material.getCode()) || !selected.containsKey(material.getCode()))
                ParameterChecks.reject(ErrorCode.PRODUCTION_INPUT_STALE);
            view.add(new Item(row.getId(), row.getLineNo(), row.getMaterialId(), material.getCode(),
                    row.getUnitId(), row.getTotalRequiredQty(), java.util.Collections.unmodifiableList(selected.get(material.getCode()))));
        }
        if (!seen.equals(selected.keySet())) ParameterChecks.reject(ErrorCode.PRODUCTION_INPUT_STALE);
        var execution = executions.selectByCode(normalized);
        String executionCode = null;
        String status = null;
        boolean closed = false;
        if (execution != null) {
            if (!Objects.equals(execution.getWorkorderId(), order.getId()))
                ParameterChecks.reject(ErrorCode.RESOURCE_CONFLICT);
            executionCode = execution.getExecutionCode();
            status = execution.getStatus();
            closed = ended(status);
        }
        return new Detail(normalized, order.getId(), order.getCode(), order.getProductId(),
                materials.selectByPrimaryKey(order.getProductId()).getUnitId(), order.getMaterialRevision(),
                executionCode, status, closed, List.copyOf(view));
    }

    public static boolean ended(String status) {
        return ProductionExecutionStatus.FULLY_RECEIVED.getValue().equals(status)
                || ProductionExecutionStatus.CANCELLED.getValue().equals(status);
    }

    @Transactional(transactionManager = "businessTransactionManager", readOnly = true)
    public Calculation calculate(String code, BigDecimal requested) {
        var view = detail(code);
        return calculate(view, order(code), requested);
    }

    public Calculation calculate(Detail view, WorkorderEntity order, BigDecimal requested) {
        if (view.closed()) ParameterChecks.reject(ErrorCode.PRODUCTION_COMBINATION_CLOSED);
        sources.localCreation();
        sources.order(order.getId());
        if (!WorkOrderStatus.RELEASED.getValue().equals(order.getStatus()))
            ParameterChecks.reject(ErrorCode.WORKORDER_NOT_RELEASED);
        var context = context(order, view.executionCode());
        var selected = view.items().stream().map(i -> new Selection(i.demandLineId(), i.batches())).toList();
        var maximum = ProductionCombinationPlanner.fixed(context.demands(), context.stocks(), selected, order.getPlannedQty(),
                        context.confirmed(), context.issued(), null, context.remaining(), context.outputPrecision())
                .outputQty();
        var quantity = requested;
        if (quantity == null) quantity = maximum;
        if (quantity.signum() < 0)
            ParameterChecks.reject(ErrorCode.BAD_REQUEST, new SafeBusinessMessage("配套数量不能为负数"));
        List<Allocation> allocation = List.of();
        if (quantity.signum() > 0) {
            quantities.checkForMaterial(quantity, order.getProductId());
            if (quantity.compareTo(maximum) > 0) throw new ProductionCombinationStockChangedException(maximum);
            allocation = ProductionCombinationPlanner.fixed(context.demands(), context.stocks(), selected, order.getPlannedQty(),
                            context.confirmed(), context.issued(), quantity, context.remaining(), context.outputPrecision())
                    .allocations();
        }
        record Key(String code, Long revision, List<Demand> demands, String output) {
        }
        String key = RequestFingerprint.sha256(new ObjectMapper().writeValueAsString(new Key(view.code(),
                order.getMaterialRevision(), context.demands(), quantity.stripTrailingZeros().toPlainString())));
        return new Calculation(view, maximum, quantity, allocation, key, allocation.stream()
                .map(Allocation::warehouseId).distinct().sorted().map(context.warehouseLabels()::get).toList());
    }

    private Context context(WorkorderEntity order, String executionCode) {
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
            if (allowed.size() > 100) ParameterChecks.reject(ErrorCode.PRODUCTION_INPUT_STALE);
            if (!allowed.isEmpty()) {
                sources.warehouses(order.getSource(), allowed);
                wc.andIdIn(allowed);
            }
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
        var capacity = allocations.activeAllocation(order.getId(), executionCode);
        if (capacity == null) ParameterChecks.reject(ErrorCode.RESOURCE_CONFLICT);
        var remaining = capacity.remainingTargetQty();
        if (executionCode != null) {
            var execution = executions.selectByCode(executionCode);
            if (execution == null || !order.getId().equals(execution.getWorkorderId()) || ended(execution.getStatus()))
                ParameterChecks.reject(ErrorCode.PRODUCTION_COMBINATION_CLOSED);
            // The allocation query excludes this execution; only its already-confirmed contribution
            // consumes its remaining capacity. Other executions keep their original target allocation.
            remaining = remaining.subtract(inputs.confirmedExecutionOutput(executionCode));
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
