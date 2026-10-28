package dev.mesbase.business.production.service;

import dev.mesbase.common.utils.StringUtils;
import dev.mesbase.common.validation.ParameterChecks;
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
import java.util.stream.Collectors;
import dev.mesbase.business.wm.dao.WmWarehouseMapper;
import dev.mesbase.business.wm.dao.WmWarehouseErpExtraMapper;
import dev.mesbase.business.wm.domain.WmWarehouseExample;
import dev.mesbase.business.wm.domain.WmWarehouseErpExtraExample;
import dev.mesbase.business.wm.dao.WmStockDocumentMapper;
import dev.mesbase.business.wm.dao.WmStockDocumentLineMapper;
import dev.mesbase.business.wm.domain.WmStockDocumentExample;
import dev.mesbase.business.wm.domain.StockDocumentType;
import dev.mesbase.business.wm.domain.WmStockDocumentLineExample;
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
    private final WmStockDocumentMapper issues;
    private final WmStockDocumentLineMapper items;
    private final MesProductionReceiptMapper receipts;

    public BusinessSourceRules(WarehouseIntegration integration, WorkorderMapper orders,
                               WorkorderErpExtraMapper orderExtras, WmWarehouseMapper warehouses,
                               WmWarehouseErpExtraMapper warehouseExtras,
                               WmStockDocumentMapper issues, WmStockDocumentLineMapper items,
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
        if (source == null) ParameterChecks.reject(ErrorCode.BUSINESS_SOURCE_CONFLICT);
        return source;
    }

    public void current(String value) {
        var source = required(value);
        var mode = integration.configuredMode(FactoryContext.required());
        if (mode == null || (source == BusinessSource.LOCAL) != (mode == Mode.LOCAL_ONLY)) ParameterChecks.reject(ErrorCode.BUSINESS_SOURCE_CONFLICT);
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
        if (row == null) ParameterChecks.reject(ErrorCode.BUSINESS_SOURCE_CONFLICT);
        if (!output || !WorkOrderStatus.COMPLETED.getValue().equals(row.getStatus()))
            ProductionChainLocks.requireActive(row);
        current(row.getSource());
        if (required(row.getSource()) == BusinessSource.EXTERNAL) {
            var extra = orderExtras.selectByPrimaryKey(id);
            if (extra == null || StringUtils.isBlank(extra.getExternalId())) ParameterChecks.reject(ErrorCode.BUSINESS_SOURCE_CONFLICT);
            binding(extra.getProvider(), extra.getBindingKey(), extra.getConfigurationRevision());
        }
        return row;
    }

    public void warehouse(String expected, Long id) {
        if (id == null) ParameterChecks.reject(ErrorCode.BUSINESS_SOURCE_CONFLICT);
        warehouses(expected, List.of(id));
    }

    public void warehouses(String expected, List<Long> ids) {
        current(expected);
        if (ids == null || ids.stream().anyMatch(Objects::isNull)) ParameterChecks.reject(ErrorCode.BUSINESS_SOURCE_CONFLICT);
        var unique = ids.stream().distinct().toList();
        if (unique.isEmpty()) return;
        if (unique.size() > 10000) ParameterChecks.reject(ErrorCode.BUSINESS_SOURCE_CONFLICT);
        var f = new WmWarehouseExample();
        f.createCriteria().andIdIn(unique);
        var rows = warehouses.selectByExample(f);
        if (rows.size() != unique.size()) throw new FoundationException(ErrorCode.STOCK_REFERENCE_INVALID);
        for (var row : rows) if (required(row.getSource()) != required(expected)) ParameterChecks.reject(ErrorCode.BUSINESS_SOURCE_CONFLICT);
        if (required(expected) == BusinessSource.EXTERNAL) {
            var e = new WmWarehouseErpExtraExample();
            e.createCriteria().andIdIn(unique);
            var extras = warehouseExtras.selectByExample(e);
            if (extras.size() != unique.size()) ParameterChecks.reject(ErrorCode.BUSINESS_SOURCE_CONFLICT);
            for (var x : extras) {
                if (StringUtils.isBlank(x.getExternalId())) ParameterChecks.reject(ErrorCode.BUSINESS_SOURCE_CONFLICT);
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
        if (unique.isEmpty() || unique.size() > 10000 || unique.stream().anyMatch(Objects::isNull)) ParameterChecks.reject(ErrorCode.BUSINESS_SOURCE_CONFLICT);
        var filter = new WmWarehouseErpExtraExample();
        filter.createCriteria().andIdIn(unique);
        var rows = warehouseExtras.selectByExample(filter);
        if (rows.size() != unique.size()) ParameterChecks.reject(ErrorCode.BUSINESS_SOURCE_CONFLICT);
        for (var row : rows) {
            if (StringUtils.isBlank(row.getExternalId())) ParameterChecks.reject(ErrorCode.BUSINESS_SOURCE_CONFLICT);
            binding(row.getProvider(), row.getBindingKey(), row.getConfigurationRevision());
        }
        return rows.stream().collect(Collectors.toMap(row -> row.getId(), row -> row.getExternalId()));
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
        var f = new WmStockDocumentExample();
        f.createCriteria().andSourceExecutionIdEqualTo(execution.getId()).andDocumentTypeEqualTo(StockDocumentType.MATERIAL_ISSUE.getValue());
        var linked = bounded(() -> issues.selectByExample(f));
        if (linked.size() > 10000) ParameterChecks.reject(ErrorCode.BUSINESS_SOURCE_CONFLICT);
        if (!linked.isEmpty()) {
            var i = new WmStockDocumentLineExample();
            i.createCriteria().andDocumentIdIn(linked.stream().map(x -> x.getId()).toList());
            var lines = bounded(() -> items.selectByExample(i));
            if (lines.size() > 10000) ParameterChecks.reject(ErrorCode.BUSINESS_SOURCE_CONFLICT);
            for (var line : lines) {
                if (line.getWarehouseId() == null) ParameterChecks.reject(ErrorCode.BUSINESS_SOURCE_CONFLICT);
                ids.add(line.getWarehouseId());
            }
        }
        var r = new MesProductionReceiptExample();
        r.createCriteria().andExecutionIdEqualTo(execution.getId());
        var received = bounded(() -> receipts.selectByExample(r));
        if (received.size() > 10000) ParameterChecks.reject(ErrorCode.BUSINESS_SOURCE_CONFLICT);
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
                || BusinessSource.EXTERNAL == BusinessSource.from(warehouse.getSource())) ParameterChecks.reject(ErrorCode.BUSINESS_SOURCE_CONFLICT);
        if (order.getSource() != null) required(order.getSource());
        if (warehouse.getSource() != null) required(warehouse.getSource());
    }

    public static void binding(String provider, String key, Long revision) {
        var snapshot = FactoryIntegrationContext.required();
        if (!Objects.equals(provider, snapshot.erpType()) || !Objects.equals(key, snapshot.configurationRef())
                || revision == null || !Objects.equals(revision, snapshot.configurationRevision())) ParameterChecks.reject(ErrorCode.BUSINESS_SOURCE_CONFLICT);
    }

    private static <T> List<T> bounded(Supplier<List<T>> query) {
        try (var page = PageHelper.offsetPage(0, 10001, false)) {
            var rows = List.copyOf(query.get());
            if (rows.size() > 10000) ParameterChecks.reject(ErrorCode.BUSINESS_SOURCE_CONFLICT);
            return rows;
        }
    }

}
