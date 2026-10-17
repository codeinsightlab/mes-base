package dev.mesbase.business.production.service;

import dev.mesbase.business.production.domain.WorkOrderStatus;
import dev.mesbase.business.shared.enums.EnableStatus;
import dev.mesbase.business.integration.production.ProductionSupply;
import dev.mesbase.business.integration.production.ProductionSupply.Stock;
import dev.mesbase.business.integration.production.ProductionSupply.Order;
import dev.mesbase.business.integration.production.ProductionSupply.Requirement;
import dev.mesbase.business.integration.production.ProductionSupply.Context;
import dev.mesbase.business.integration.production.ProductionSupply.Calculation;
import dev.mesbase.business.integration.production.ProductionErpAdapter;
import dev.mesbase.business.integration.warehouse.WarehouseIntegration;
import dev.mesbase.business.integration.warehouse.ErpActionResult.Mode;
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
import dev.mesbase.platform.execution.FactoryContext;
import com.github.pagehelper.PageHelper;
import java.math.BigDecimal;
import tools.jackson.databind.ObjectMapper;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Objects;
import org.springframework.core.env.Environment;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.EnumerablePropertySource;
import java.util.TreeSet;
import org.springframework.stereotype.Service;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.beans.factory.annotation.Qualifier;
import dev.mesbase.core.factory.FactoryScope;
import dev.mesbase.business.integration.warehouse.WarehouseIntegration.Selection;

@Service
@ConditionalOnProperty(name = "mes.business.enabled", havingValue = "true")
public class ProductionInputSupplyService {
    private final WarehouseIntegration integration;
    private final List<ProductionErpAdapter> adapters;
    private final Environment environment;
    private final MaterialMapper materials;
    private final UnitMeasureMapper units;
    private final WmInventoryMapper inventory;
    private final WmWarehouseMapper warehouses;
    private final WorkorderMapper orders;
    private final TransactionTemplate readTransaction;

    public ProductionInputSupplyService(WarehouseIntegration integration, List<ProductionErpAdapter> adapters,
                                        Environment environment, MaterialMapper materials, UnitMeasureMapper units,
                                        WmInventoryMapper inventory,
                                        WmWarehouseMapper warehouses, WorkorderMapper orders,
                                        @Qualifier("businessTransactionManager") PlatformTransactionManager manager) {
        this.integration = integration;
        this.adapters = List.copyOf(adapters);
        this.environment = environment;
        this.materials = materials;
        this.units = units;
        this.inventory = inventory;
        this.warehouses = warehouses;
        this.orders = orders;
        readTransaction = new TransactionTemplate(manager);
        readTransaction.setReadOnly(true);
    }

    @Transactional(transactionManager = "businessTransactionManager", propagation = Propagation.NOT_SUPPORTED)
    public Context query(String code) {
        return query(code, null, 0, 20);
    }

    @Transactional(transactionManager = "businessTransactionManager", propagation = Propagation.NOT_SUPPORTED)
    public Context query(String code, Long workorderId, int offset, int limit) {
        ProductionQuantityService.paging(offset, limit);
        var identity = ProductionScanCode.material(code);
        var scope = FactoryContext.required();
        var selection = integration.submissionSelection(scope);
        if (selection.mode() == Mode.CONNECTED) {
            var matching = adapters.stream().filter(a -> a.supports(scope, selection.bindingKey())).toList();
            if (matching.size() != 1) throw new FoundationException(ErrorCode.INTEGRATION_NOT_CONFIGURED);
            var result = matching.getFirst()
                    .query(scope, selection.bindingKey(), identity.materialCode(), identity.lotCode());
            if (result == null || !Mode.CONNECTED.getValue().equals(result.sourceMode())) fail();
            var eligible = result.orders().stream()
                    .filter(o -> workorderId == null || workorderId.equals(o.workorderId())).toList();
            return new Context(result.sourceMode(), result.stocks(), eligible.stream().skip(offset).limit(limit)
                    .toList(), eligible.size(), offset, limit);
        }
        return readTransaction.execute(status -> local(code, identity, scope, selection, workorderId, offset, limit));
    }

    private Context local(String code, ProductionScanCode.MaterialLot identity, FactoryScope scope,
                          Selection selection, Long workorderId, int offset, int limit) {
        var mf = new MaterialExample();
        mf.createCriteria().andCodeEqualTo(identity.materialCode());
        var matches = materials.selectByExample(mf).stream()
                .filter(m -> identity.materialCode().equals(m.getCode()) && EnableStatus.ENABLED.getValue()
                        .equals(m.getStatus())).toList();
        if (matches.size() != 1) fail();
        var material = matches.getFirst();
        var unit = material.getUnitId() == null ? null : units.selectByPrimaryKey(material.getUnitId());
        if (unit == null || !EnableStatus.ENABLED.getValue().equals(unit.getStatus())) fail();
        var now = LocalDateTime.now();
        var sf = new WmInventoryExample();
        sf.createCriteria().andMaterialIdEqualTo(material.getId()).andBatchNoEqualTo(identity.lotCode())
                .andStatusEqualTo(EnableStatus.ENABLED.getValue());
        if (inventory.countByExample(sf) > 100) fail();
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
            if (w != null && EnableStatus.ENABLED.getValue().equals(w.getStatus()) && available.signum() > 0)
                stocks.add(new Stock(s.getId()
                        .toString(), material.getId(), material.getCode(), s.getBatchNo(), w.getId(), w.getCode(), s.getBinCode(), unit.getId(), unit.getCode(), available, now));
        }
        String prefix = "mes.production.input.factories." + scope.factoryId().value() + ".workorders.";
        var configuredIds = configuredOrders(prefix).stream()
                .filter(id -> workorderId == null || id.equals(workorderId)).filter(id -> {
                    String key = prefix + id + ".";
                    if (!material.getId().toString().equals(environment.getProperty(key + "material-id"))) return false;
                    String allowed = environment.getProperty(key + "warehouse-ids");
                    if (allowed == null) fail();
                    var ids = Arrays.stream(allowed.split(",")).map(String::trim).map(Long::valueOf).toList();
                    return stocks.stream().anyMatch(stock -> ids.contains(stock.warehouseId()));
                }).toList();
        if (configuredIds.isEmpty())
            return new Context(selection.mode().getValue(), List.copyOf(stocks), List.of(), 0, offset, limit);
        var of = new WorkorderExample();
        var criteria = of.createCriteria().andStatusEqualTo(WorkOrderStatus.RELEASED.getValue()).andIdIn(configuredIds);
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
        if (outputUnitIds.isEmpty()) fail();
        uf.createCriteria().andIdIn(outputUnitIds);
        var outputUnits = units.selectByExample(uf).stream().collect(Collectors.toMap(u -> u.getId(), u -> u));
        for (var o : orderRows) {
            String orderPrefix = prefix + o.getId() + ".";
            String configured = environment.getProperty(orderPrefix + "material-id");
            if (!Objects.equals(configured, material.getId().toString())) continue;
            var product = productMap.get(o.getProductId());
            var total = environment.getProperty(orderPrefix + "total-required-qty", BigDecimal.class);
            var center = environment.getProperty(orderPrefix + "work-center");
            var allowed = environment.getProperty(orderPrefix + "warehouse-ids");
            if (product == null || total == null || total.signum() <= 0 || center == null || center.isBlank() || allowed == null)
                fail();
            var ids = Arrays.stream(allowed.split(",")).map(String::trim).map(Long::valueOf).toList();
            if (stocks.stream().noneMatch(s -> ids.contains(s.warehouseId()))) continue;
            if (code.startsWith("MES1.") && !ProductionScanCode.parse(code).productCode().equals(product.getCode()))
                continue;
            var outputUnit = outputUnits.get(product.getUnitId());
            if (outputUnit == null || !EnableStatus.ENABLED.getValue().equals(outputUnit.getStatus())) fail();
            candidates.add(new Order(o.getId(), o.getCode(), product.getCode(), product.getName(), center, o.getPlannedQty(), outputUnit.getCode(), o.getStatus(), ids,
                    List.of(new Requirement(material.getCode(), material.getId(), unit.getId(), unit.getCode(), total)), "LOCAL-WORKORDER:" + o.getId(), now));
        }
        return new Context(selection.mode()
                .getValue(), List.copyOf(stocks), List.copyOf(candidates), candidateTotal, offset, limit);
    }

    @Transactional(transactionManager = "businessTransactionManager", propagation = Propagation.NOT_SUPPORTED)
    public Calculation calculate(String code, Long orderId, String stockReference, BigDecimal outputQty) {
        var context = query(code, orderId, 0, 1);
        var order = context.orders().stream().filter(o -> o.workorderId().equals(orderId)).findFirst()
                .orElseThrow(() -> new FoundationException(ErrorCode.BAD_REQUEST));
        var stock = context.stocks().stream().filter(s -> s.reference().equals(stockReference)).findFirst()
                .orElseThrow(() -> new FoundationException(ErrorCode.BAD_REQUEST));
        var required = order.requirements().stream()
                .filter(r -> r.totalRequiredQty() != null && r.totalRequiredQty().signum() > 0).toList();
        if (required.stream().map(Requirement::materialCode).distinct().count() > 1)
            throw new FoundationException(ErrorCode.PRODUCTION_SINGLE_MATERIAL_REQUIRED);
        if (required.isEmpty() || required.stream().map(Requirement::materialCode).distinct().count() != 1
                || required.stream().anyMatch(r -> !r.materialCode()
                .equals(stock.materialCode()) || !Objects.equals(r.unitId(), stock.unitId()) || !Objects.equals(r.unitCode(), stock.unitCode()))
                || !order.allowedWarehouses().contains(stock.warehouseId())) fail();
        var total = required.stream().map(Requirement::totalRequiredQty).reduce(BigDecimal.ZERO, BigDecimal::add);
        var quantity = issueQuantity(order.plannedQty(), total, outputQty);
        if (quantity.compareTo(stock.availableQty()) > 0) fail();
        return new Calculation(context.sourceMode(), order, stock, outputQty, quantity);
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
                if (!id.matches("[1-9][0-9]{0,18}")) fail();
                try {
                    ids.add(Long.valueOf(id));
                } catch (NumberFormatException invalid) {
                    fail();
                }
                if (ids.size() > 10000) throw new FoundationException(ErrorCode.INTEGRATION_NOT_CONFIGURED);
            }
        }
        return List.copyOf(ids);
    }

    public static String calculationKey(Calculation c) {
        var s = c.stock();
        var o = c.order();
        return ProductionScanCode.digest(new ObjectMapper().writeValueAsString(List.of(
                c.sourceMode(), o.workorderId(), o.productCode(), o.workCenter(), o.plannedQty().stripTrailingZeros()
                        .toPlainString(),
                o.requirements(), s.reference(), s.materialCode(), s.lotCode(), s.warehouseId(), s.binCode() == null ? "" : s.binCode(), s.unitId(), s.unitCode(),
                c.outputQty().stripTrailingZeros().toPlainString(), c.issueQty().toPlainString())));
    }

    public static BigDecimal issueQuantity(BigDecimal plan, BigDecimal total, BigDecimal output) {
        if (plan == null || total == null || output == null || plan.signum() <= 0 || total.signum() <= 0 || output.signum() <= 0 || output.scale() > 6 || output.precision() - output.scale() > 14)
            fail();
        var result = total.multiply(output).divide(plan, 6, RoundingMode.HALF_UP);
        if (result.signum() <= 0 || result.precision() - result.scale() > 14) fail();
        return result;
    }

    private static void fail() {
        throw new FoundationException(ErrorCode.BAD_REQUEST);
    }
}
