package dev.mesbase.business.production.service;

import dev.mesbase.business.shared.enums.BusinessSource;
import dev.mesbase.business.production.domain.WorkOrderStatus;
import dev.mesbase.business.integration.warehouse.WarehouseIntegration;
import dev.mesbase.business.integration.warehouse.ErpActionResult.Mode;
import dev.mesbase.business.production.dao.WorkorderMapper;
import dev.mesbase.business.production.dao.WorkorderErpExtraMapper;
import dev.mesbase.business.production.dao.MesProductionReceiptMapper;
import dev.mesbase.business.production.domain.MesProductionReceiptExample;
import dev.mesbase.business.production.domain.MesProductionExecutionEntity;
import dev.mesbase.business.production.domain.WorkorderEntity;
import dev.mesbase.business.production.domain.WorkorderExample;
import dev.mesbase.business.production.domain.WorkorderErpExtraExample;
import dev.mesbase.business.integration.production.ProductionSupply.Context;
import java.util.stream.Collectors;
import dev.mesbase.business.wm.dao.WmWarehouseMapper;
import dev.mesbase.business.wm.dao.WmWarehouseErpExtraMapper;
import dev.mesbase.business.wm.domain.WmWarehouseExample;
import dev.mesbase.business.wm.domain.WmWarehouseErpExtraExample;
import dev.mesbase.business.issue.dao.MesMaterialIssueMapper;
import dev.mesbase.business.issue.dao.MesMaterialIssueItemMapper;
import dev.mesbase.business.issue.domain.MesMaterialIssueExample;
import dev.mesbase.business.issue.domain.MesMaterialIssueItemExample;
import dev.mesbase.core.error.ErrorCode;
import dev.mesbase.core.error.FoundationException;
import dev.mesbase.platform.execution.FactoryContext;
import dev.mesbase.platform.execution.FactoryIntegrationContext;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.ArrayList;
import java.util.function.Supplier;
import com.github.pagehelper.PageHelper;
import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

/**
 * Business admission only. Historical reads/replay/recovery must not re-enter current-mode admission.
 */
@Component
@ConditionalOnProperty(name = "mes.business.enabled", havingValue = "true")
public class BusinessSourceRules {
    private final WarehouseIntegration integration;
    private final WorkorderMapper orders;
    private final WorkorderErpExtraMapper orderExtras;
    private final WmWarehouseMapper warehouses;
    private final WmWarehouseErpExtraMapper warehouseExtras;
    private final MesMaterialIssueMapper issues;
    private final MesMaterialIssueItemMapper items;
    private final MesProductionReceiptMapper receipts;

    public BusinessSourceRules(WarehouseIntegration integration, WorkorderMapper orders,
                               WorkorderErpExtraMapper orderExtras, WmWarehouseMapper warehouses,
                               WmWarehouseErpExtraMapper warehouseExtras,
                               MesMaterialIssueMapper issues, MesMaterialIssueItemMapper items,
                               MesProductionReceiptMapper receipts) {
        this.integration = integration;
        this.orders = orders;
        this.orderExtras = orderExtras;
        this.warehouses = warehouses;
        this.warehouseExtras = warehouseExtras;
        this.issues = issues;
        this.items = items;
        this.receipts = receipts;
    }

    public static BusinessSource required(String value) {
        var source = BusinessSource.from(value);
        if (source == null) conflict();
        return source;
    }

    public void current(String value) {
        var source = required(value);
        var mode = integration.configuredMode(FactoryContext.required());
        if (mode == null || (source == BusinessSource.LOCAL) != (mode == Mode.LOCAL_ONLY)) conflict();
    }

    public void localCreation() {
        current(BusinessSource.LOCAL.getValue());
    }

    public static void localEditable(String value) {
        if (required(value) != BusinessSource.LOCAL) throw new FoundationException(ErrorCode.EXTERNAL_SOURCE_READ_ONLY);
    }

    public WorkorderEntity order(Long id) {
        return order(id, false);
    }

    private WorkorderEntity order(Long id, boolean output) {
        var row = orders.selectByPrimaryKey(id);
        if (row == null) conflict();
        if (!output || !WorkOrderStatus.COMPLETED.getValue().equals(row.getStatus()))
            ProductionChainLocks.requireActive(row);
        current(row.getSource());
        if (required(row.getSource()) == BusinessSource.EXTERNAL) {
            var extra = orderExtras.selectByPrimaryKey(id);
            if (extra == null || extra.getExternalId() == null || extra.getExternalId().isBlank()) conflict();
            binding(extra.getProvider(), extra.getBindingKey(), extra.getConfigurationRevision());
        }
        return row;
    }

    public void warehouse(String expected, Long id) {
        if (id == null) conflict();
        warehouses(expected, List.of(id));
    }

    public void warehouses(String expected, List<Long> ids) {
        current(expected);
        if (ids == null || ids.stream().anyMatch(Objects::isNull)) conflict();
        var unique = ids.stream().distinct().toList();
        if (unique.isEmpty()) return;
        if (unique.size() > 10000) conflict();
        var f = new WmWarehouseExample();
        f.createCriteria().andIdIn(unique);
        var rows = warehouses.selectByExample(f);
        if (rows.size() != unique.size()) throw new FoundationException(ErrorCode.STOCK_REFERENCE_INVALID);
        for (var row : rows) if (required(row.getSource()) != required(expected)) conflict();
        if (required(expected) == BusinessSource.EXTERNAL) {
            var e = new WmWarehouseErpExtraExample();
            e.createCriteria().andIdIn(unique);
            var extras = warehouseExtras.selectByExample(e);
            if (extras.size() != unique.size()) conflict();
            for (var x : extras) {
                if (x.getExternalId() == null || x.getExternalId().isBlank()) conflict();
                binding(x.getProvider(), x.getBindingKey(), x.getConfigurationRevision());
            }
        }
    }

    /**
     * ERP preview candidates must resolve to the same trusted local identities as formal commands.
     */
    public Map<Long, String> externalWarehouseIds(List<Long> ids) {
        current(BusinessSource.EXTERNAL.getValue());
        var unique = ids.stream().distinct().toList();
        if (unique.isEmpty() || unique.size() > 10000 || unique.stream().anyMatch(Objects::isNull)) conflict();
        var filter = new WmWarehouseErpExtraExample();
        filter.createCriteria().andIdIn(unique);
        var rows = warehouseExtras.selectByExample(filter);
        if (rows.size() != unique.size()) conflict();
        for (var row : rows) {
            if (row.getExternalId() == null || row.getExternalId().isBlank()) conflict();
            binding(row.getProvider(), row.getBindingKey(), row.getConfigurationRevision());
        }
        return rows.stream().collect(Collectors.toMap(row -> row.getId(), row -> row.getExternalId()));
    }

    public void externalCandidates(Context context) {
        current(BusinessSource.EXTERNAL.getValue());
        if (context == null || context.orders() == null || context.stocks() == null || context.orders()
                .size() > 10000 || context.stocks().size() > 10000) conflict();
        var ids = context.orders().stream().map(row -> row.workorderId()).distinct().toList();
        if (ids.stream().anyMatch(Objects::isNull)) conflict();
        if (!ids.isEmpty()) {
            var filter = new WorkorderExample();
            filter.createCriteria().andIdIn(ids);
            var rows = orders.selectByExample(filter);
            if (rows.size() != ids.size()) conflict();
            var mapping = new WorkorderErpExtraExample();
            mapping.createCriteria().andIdIn(ids);
            var extras = orderExtras.selectByExample(mapping).stream()
                    .collect(Collectors.toMap(row -> row.getId(), row -> row));
            for (var row : rows) {
                if (required(row.getSource()) != BusinessSource.EXTERNAL) conflict();
                var extra = extras.get(row.getId());
                if (extra == null || extra.getExternalId() == null || extra.getExternalId().isBlank()) conflict();
                binding(extra.getProvider(), extra.getBindingKey(), extra.getConfigurationRevision());
            }
        }
        warehouses(BusinessSource.EXTERNAL.getValue(), context.stocks().stream().map(row -> row.warehouseId())
                .toList());
    }

    public void execution(MesProductionExecutionEntity execution, Long finishedWarehouse) {
        execution(execution, finishedWarehouse, false);
    }

    /**
     * Production completion does not close the separately authorized output/warehouse flow.
     */
    public void finishedOutput(MesProductionExecutionEntity execution, Long finishedWarehouse) {
        execution(execution, finishedWarehouse, true);
    }

    private void execution(MesProductionExecutionEntity execution, Long finishedWarehouse, boolean output) {
        var order = order(execution.getWorkorderId(), output);
        var ids = new ArrayList<Long>();
        if (finishedWarehouse != null) ids.add(finishedWarehouse);
        var f = new MesMaterialIssueExample();
        f.createCriteria().andExecutionIdEqualTo(execution.getId());
        var linked = bounded(() -> issues.selectByExample(f));
        if (linked.size() > 10000) conflict();
        if (!linked.isEmpty()) {
            var i = new MesMaterialIssueItemExample();
            i.createCriteria().andIssueIdIn(linked.stream().map(x -> x.getId()).toList());
            var lines = bounded(() -> items.selectByExample(i));
            if (lines.size() > 10000) conflict();
            for (var line : lines) {
                if (line.getWarehouseId() == null) conflict();
                ids.add(line.getWarehouseId());
            }
        }
        var r = new MesProductionReceiptExample();
        r.createCriteria().andExecutionIdEqualTo(execution.getId());
        var received = bounded(() -> receipts.selectByExample(r));
        if (received.size() > 10000) conflict();
        received.forEach(x -> ids.add(x.getWarehouseId()));
        warehouses(order.getSource(), ids);
    }

    /**
     * Caller proves an original persisted LOCAL intent; never authorize new work through this method.
     */
    public void frozenLocal(Long orderId, Long warehouseId) {
        var order = orders.selectByPrimaryKey(orderId);
        var warehouse = warehouses.selectByPrimaryKey(warehouseId);
        if (order == null || warehouse == null || BusinessSource.EXTERNAL == BusinessSource.from(order.getSource())
                || BusinessSource.EXTERNAL == BusinessSource.from(warehouse.getSource())) conflict();
        if (order.getSource() != null) required(order.getSource());
        if (warehouse.getSource() != null) required(warehouse.getSource());
    }

    public static void binding(String provider, String key, Long revision) {
        var snapshot = FactoryIntegrationContext.required();
        if (!Objects.equals(provider, snapshot.erpType()) || !Objects.equals(key, snapshot.configurationRef())
                || revision == null || !Objects.equals(revision, snapshot.configurationRevision())) conflict();
    }

    private static <T> List<T> bounded(Supplier<List<T>> query) {
        try (var page = PageHelper.offsetPage(0, 10001, false)) {
            var rows = List.copyOf(query.get());
            if (rows.size() > 10000) conflict();
            return rows;
        }
    }

    private static void conflict() {
        throw new FoundationException(ErrorCode.BUSINESS_SOURCE_CONFLICT);
    }
}
