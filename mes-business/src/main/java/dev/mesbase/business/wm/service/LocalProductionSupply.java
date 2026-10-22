package dev.mesbase.business.wm.service;

import dev.mesbase.common.validation.ParameterChecks;
import java.util.Optional;

import dev.mesbase.business.production.domain.WorkOrderStatus;
import dev.mesbase.business.shared.enums.EnableStatus;
import dev.mesbase.business.integration.production.ProductionSupply.Stock;
import dev.mesbase.business.integration.production.ProductionSupply.Calculation;
import dev.mesbase.business.integration.production.ProductionSupply.Order;
import dev.mesbase.business.integration.production.ProductionSupply.Requirement;
import dev.mesbase.business.integration.production.ProductionSupply.Context;
import dev.mesbase.business.basic.dao.MaterialMapper;
import dev.mesbase.business.basic.dao.UnitMeasureMapper;
import dev.mesbase.business.basic.domain.MaterialExample;
import dev.mesbase.business.basic.domain.UnitMeasureExample;
import dev.mesbase.business.wm.dao.WmInventoryMapper;
import dev.mesbase.business.wm.dao.WmWarehouseMapper;
import dev.mesbase.business.wm.domain.WmInventoryExample;
import dev.mesbase.business.wm.domain.WmWarehouseExample;
import java.util.stream.Collectors;
import dev.mesbase.business.production.dao.WorkorderMapper;
import dev.mesbase.business.production.domain.WorkorderExample;
import dev.mesbase.business.production.domain.WorkorderEntity;
import dev.mesbase.business.production.domain.ProductionScanCode;
import dev.mesbase.core.error.ErrorCode;
import dev.mesbase.core.error.FoundationException;
import com.github.pagehelper.PageHelper;
import java.math.BigDecimal;
import dev.mesbase.business.shared.enums.BusinessSource;
import java.time.LocalDateTime;
import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Objects;
import org.springframework.core.env.Environment;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.EnumerablePropertySource;
import java.util.TreeSet;
import java.util.HashMap;
import dev.mesbase.business.production.dao.MesWorkorderMaterialMapper;
import dev.mesbase.business.production.domain.MesWorkorderMaterialExample;
import dev.mesbase.business.production.domain.MesWorkorderMaterialEntity;
import tools.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import dev.mesbase.core.factory.FactoryScope;
import dev.mesbase.business.integration.warehouse.WarehouseIntegration.Selection;

@Service
@ConditionalOnProperty(name = "mes.business.enabled", havingValue = "true")
public class LocalProductionSupply {
    private final Environment environment;
    private final MaterialMapper materials;
    private final UnitMeasureMapper units;
    private final WmInventoryMapper inventory;
    private final WmWarehouseMapper warehouses;
    private final WorkorderMapper orders;
    private final MesWorkorderMaterialMapper requirements;

    public LocalProductionSupply(Environment environment, MaterialMapper materials, UnitMeasureMapper units,
                                 WmInventoryMapper inventory, WmWarehouseMapper warehouses, WorkorderMapper orders,
                                 MesWorkorderMaterialMapper requirements) {
        this.requirements = requirements;
        this.environment = environment;
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
        var warehouse = Optional.ofNullable(stock).map(nullableValue -> warehouses.selectByPrimaryKey(nullableValue.getWarehouseId())).orElse(null);
        if (order == null || warehouse == null || !BusinessSource.LOCAL.getValue().equals(order.getSource())
                || !BusinessSource.LOCAL.getValue().equals(warehouse.getSource()))
            throw new FoundationException(ErrorCode.BUSINESS_SOURCE_CONFLICT);
    }

    public Context query(String code, ProductionScanCode.MaterialLot identity, FactoryScope scope,
                         Selection selection, Long workorderId, int offset, int limit) {
        var mf = new MaterialExample();
        mf.createCriteria().andCodeEqualTo(identity.materialCode());
        var matches = materials.selectByExample(mf).stream()
                .filter(m -> identity.materialCode().equals(m.getCode()) && EnableStatus.ENABLED.getValue()
                        .equals(m.getStatus())).toList();
        if (matches.size() != 1) ParameterChecks.reject(ErrorCode.BAD_REQUEST);
        var material = matches.getFirst();
        var unit = Optional.ofNullable(material.getUnitId()).map(nullableValue -> units.selectByPrimaryKey(nullableValue)).orElse(null);
        if (unit == null || !EnableStatus.ENABLED.getValue().equals(unit.getStatus())) ParameterChecks.reject(ErrorCode.BAD_REQUEST);
        var now = LocalDateTime.now();
        var sf = new WmInventoryExample();
        sf.createCriteria().andMaterialIdEqualTo(material.getId()).andBatchNoEqualTo(identity.lotCode())
                .andStatusEqualTo(EnableStatus.ENABLED.getValue());
        if (inventory.countByExample(sf) > 100) ParameterChecks.reject(ErrorCode.BAD_REQUEST);
        sf.setOrderByClause("id desc");
        var stockRows = inventory.selectByExample(sf);
        var warehouseFilter = new WmWarehouseExample();
        var warehouseIds = stockRows.stream().map(r -> r.getWarehouseId()).distinct().toList();
        if (warehouseIds.isEmpty())
            return new Context(selection.mode().getValue(), List.of(), List.of(), 0, offset, limit);
        warehouseFilter.createCriteria().andIdIn(warehouseIds);
        var warehouseMap = warehouses.selectByExample(warehouseFilter).stream()
                .collect(Collectors.toMap(r -> r.getId(), r -> r));
        var stocks = new ArrayList<Stock>();
        for (var s : stockRows) {
            if (!identity.lotCode().equals(s.getBatchNo())) continue;
            var w = warehouseMap.get(s.getWarehouseId());
            var available = s.getAvailableQuantity() == null ? s.getQuantity() : s.getAvailableQuantity()
                    .min(s.getQuantity());
            if (w != null && BusinessSource.LOCAL.getValue().equals(w.getSource()) && EnableStatus.ENABLED.getValue()
                    .equals(w.getStatus()) && available.signum() > 0)
                stocks.add(new Stock(s.getId()
                        .toString(), material.getId(), material.getCode(), s.getBatchNo(), w.getId(), w.getCode(), s.getBinCode(), unit.getId(), unit.getCode(), available, now));
        }
        String prefix = "mes.production.input.factories." + scope.factoryId().value() + ".workorders.";
        var legacyIds = environment.getProperty("mes.production.input.legacy-config.enabled", Boolean.class, true)
                ? configuredOrders(prefix) : List.<Long>of();
        var dbFilter = new MesWorkorderMaterialExample();
        dbFilter.createCriteria().andMaterialIdEqualTo(material.getId());
        if (!legacyIds.isEmpty()) dbFilter.or().andIdIn(legacyIds);
        var database = new HashMap<Long, MesWorkorderMaterialEntity>();
        try (var page = PageHelper.offsetPage(0, 10001, false)) {
            var rows = requirements.selectByExample(dbFilter);
            if (rows.size() > 10000) ParameterChecks.reject(ErrorCode.BAD_REQUEST);
            rows.forEach(r -> database.put(r.getId(), r));
        }
        var candidateIds = new TreeSet<Long>(legacyIds);
        candidateIds.addAll(database.keySet());
        var configuredIds = candidateIds.stream()
                .filter(id -> workorderId == null || id.equals(workorderId)).filter(id -> {
                    var db = database.get(id);
                    if (db != null) return EnableStatus.ENABLED.getValue().equals(db.getStatus()) && material.getId()
                            .equals(db.getMaterialId())
                            && material.getUnitId().equals(db.getUnitId()) && stocks.stream()
                            .anyMatch(stock -> warehouseIds(db).contains(stock.warehouseId()));
                    String key = prefix + id + ".";
                    if (!material.getId().toString().equals(environment.getProperty(key + "material-id"))) return false;
                    String allowed = environment.getProperty(key + "warehouse-ids");
                    if (allowed == null) ParameterChecks.reject(ErrorCode.BAD_REQUEST);
                    var ids = Arrays.stream(allowed.split(",")).map(String::trim).map(Long::valueOf).toList();
                    return stocks.stream().anyMatch(stock -> ids.contains(stock.warehouseId()));
                }).toList();
        if (configuredIds.isEmpty())
            return new Context(selection.mode().getValue(), List.copyOf(stocks), List.of(), 0, offset, limit);
        var of = new WorkorderExample();
        var criteria = of.createCriteria().andSourceEqualTo(BusinessSource.LOCAL.getValue())
                .andStatusEqualTo(WorkOrderStatus.RELEASED.getValue()).andIdIn(configuredIds);
        if (code.startsWith("MES1.")) {
            var pf = new MaterialExample();
            pf.createCriteria().andCodeEqualTo(ProductionScanCode.parse(code).productCode());
            var productIds = materials.selectByExample(pf).stream()
                    .filter(p -> p.getCode().equals(ProductionScanCode.parse(code).productCode())).map(p -> p.getId())
                    .toList();
            if (productIds.isEmpty())
                return new Context(selection.mode().getValue(), List.copyOf(stocks), List.of(), 0, offset, limit);
            criteria.andProductIdIn(productIds);
        }
        of.setOrderByClause("id DESC");
        var candidates = new ArrayList<Order>();
        List<WorkorderEntity> orderRows;
        long candidateTotal;
        try (var page = PageHelper.offsetPage(offset, limit)) {
            orderRows = List.copyOf(orders.selectByExample(of));
            candidateTotal = page.getTotal();
        }
        if (orderRows.isEmpty()) return new Context(selection.mode()
                .getValue(), List.copyOf(stocks), List.of(), candidateTotal, offset, limit);
        var productFilter = new MaterialExample();
        productFilter.createCriteria().andIdIn(orderRows.stream().map(o -> o.getProductId()).distinct().toList());
        var productMap = materials.selectByExample(productFilter).stream()
                .collect(Collectors.toMap(m -> m.getId(), m -> m));
        var outputUnitIds = productMap.values().stream().map(m -> m.getUnitId()).filter(Objects::nonNull).distinct()
                .toList();
        var uf = new UnitMeasureExample();
        if (outputUnitIds.isEmpty()) ParameterChecks.reject(ErrorCode.BAD_REQUEST);
        uf.createCriteria().andIdIn(outputUnitIds);
        var outputUnits = units.selectByExample(uf).stream().collect(Collectors.toMap(u -> u.getId(), u -> u));
        for (var o : orderRows) {
            String orderPrefix = prefix + o.getId() + ".";
            var db = database.get(o.getId());
            String configured = db == null ? environment.getProperty(orderPrefix + "material-id") : db.getMaterialId()
                    .toString();
            if (!Objects.equals(configured, material.getId().toString())) continue;
            var product = productMap.get(o.getProductId());
            var total = db == null ? environment.getProperty(orderPrefix + "total-required-qty", BigDecimal.class) : db.getTotalRequiredQty();
            if (db != null && db.getPlannedQty().compareTo(o.getPlannedQty()) != 0) continue;
            var center = db == null ? environment.getProperty(orderPrefix + "work-center") : db.getWorkCenter();
            var allowed = db == null ? environment.getProperty(orderPrefix + "warehouse-ids") : "database";
            if (product == null || total == null || total.signum() <= 0 || center == null || center.isBlank() || allowed == null)
                ParameterChecks.reject(ErrorCode.BAD_REQUEST);
            var ids = db == null ? Arrays.stream(allowed.split(",")).map(String::trim).map(Long::valueOf)
                    .toList() : warehouseIds(db);
            if (stocks.stream().noneMatch(s -> ids.contains(s.warehouseId()))) continue;
            if (code.startsWith("MES1.") && !ProductionScanCode.parse(code).productCode().equals(product.getCode()))
                continue;
            var outputUnit = outputUnits.get(product.getUnitId());
            if (outputUnit == null || !EnableStatus.ENABLED.getValue().equals(outputUnit.getStatus())) ParameterChecks.reject(ErrorCode.BAD_REQUEST);
            candidates.add(new Order(o.getId(), o.getCode(), product.getCode(), product.getName(), center, o.getPlannedQty(), outputUnit.getCode(), o.getStatus(), ids,
                    List.of(new Requirement(material.getCode(), material.getId(), unit.getId(), unit.getCode(), total)), "LOCAL-WORKORDER:" + o.getId() + (db == null ? ":LEGACY" : ":R" + db.getRevision()), now));
        }
        return new Context(selection.mode()
                .getValue(), List.copyOf(stocks), List.copyOf(candidates), candidateTotal, offset, limit);
    }

    // Transition boundary: only configured workorders are indexed, never all released business rows.
    private List<Long> configuredOrders(String prefix) {
        if (!(environment instanceof ConfigurableEnvironment configurable))
            throw new FoundationException(ErrorCode.INTEGRATION_NOT_CONFIGURED);
        var ids = new TreeSet<Long>();
        for (var source : configurable.getPropertySources()) {
            if (!(source instanceof EnumerablePropertySource<?> enumerable)) continue;
            for (String name : enumerable.getPropertyNames()) {
                if (!name.startsWith(prefix) || !name.endsWith(".material-id")) continue;
                String id = name.substring(prefix.length(), name.length() - ".material-id".length());
                if (!id.matches("[1-9][0-9]{0,18}")) ParameterChecks.reject(ErrorCode.BAD_REQUEST);
                try {
                    ids.add(Long.valueOf(id));
                } catch (NumberFormatException invalid) {
                    ParameterChecks.reject(ErrorCode.BAD_REQUEST);
                }
                if (ids.size() > 10000) throw new FoundationException(ErrorCode.INTEGRATION_NOT_CONFIGURED);
            }
        }
        return List.copyOf(ids);
    }

    public void validateFrozenCalculation(Calculation calculation) {
        var row = requirements.selectByPrimaryKey(calculation.order().workorderId());
        String version = "LOCAL-WORKORDER:" + calculation.order()
                .workorderId() + (row == null ? ":LEGACY" : ":R" + row.getRevision());
        if (!version.equals(calculation.order().sourceReference()) || row != null && (!EnableStatus.ENABLED.getValue()
                .equals(row.getStatus())
                || !row.getMaterialId().equals(calculation.stock().materialId()) || !row.getUnitId()
                .equals(calculation.stock().unitId())
                || row.getPlannedQty().compareTo(calculation.order().plannedQty()) != 0
                || row.getTotalRequiredQty()
                .compareTo(calculation.order().requirements().getFirst().totalRequiredQty()) != 0))
            throw new FoundationException(ErrorCode.PRODUCTION_INPUT_STALE);
    }

    private static List<Long> warehouseIds(MesWorkorderMaterialEntity row) {
        return Arrays.stream(new ObjectMapper().readValue(row.getWarehouseIds(), Long[].class)).toList();
    }

}
