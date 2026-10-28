package dev.mesbase.server.integration.e10;

import com.fasterxml.jackson.databind.JsonNode;
import dev.mesbase.business.basic.dao.MaterialMapper;
import dev.mesbase.business.basic.dao.UnitMeasureMapper;
import dev.mesbase.business.basic.domain.MaterialEntity;
import dev.mesbase.business.basic.domain.MaterialExample;
import dev.mesbase.business.production.dao.RoutingMapper;
import dev.mesbase.business.production.dao.RoutingProductMapper;
import dev.mesbase.business.production.dao.WorkorderMapper;
import dev.mesbase.business.production.domain.RoutingExample;
import dev.mesbase.business.production.domain.RoutingProductExample;
import dev.mesbase.business.production.domain.RoutingStatus;
import dev.mesbase.business.production.domain.WorkOrderModels;
import dev.mesbase.business.production.domain.WorkOrderStatus;
import dev.mesbase.business.production.domain.WorkorderExample;
import dev.mesbase.business.production.service.WorkOrderService;
import dev.mesbase.business.shared.ExternalSyncAction;
import dev.mesbase.business.shared.ExternalSyncCheck;
import dev.mesbase.business.shared.enums.BusinessSource;
import dev.mesbase.business.shared.enums.EnableStatus;
import dev.mesbase.business.wm.dao.WmWarehouseMapper;
import dev.mesbase.business.wm.domain.WmWarehouseExample;
import dev.mesbase.business.wm.service.IWmWarehouseService;
import dev.mesbase.business.wm.sync.ExternalWmWarehouse;
import dev.mesbase.common.utils.StringUtils;
import dev.mesbase.core.error.FoundationException;
import dev.mesbase.server.integration.e10.DigiwinE10SyncPreview.Kind;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.transaction.annotation.Transactional;

/**
 * Bounded catalog access (at most 100 candidates/run). All Dry Run and writer revalidation use this one mapping path.
 */
public class DigiwinE10SyncMapping {
    public record Item(String code, String externalId, String action, List<String> reasons,
                       String productId, String routingId, String quantity, String unit) {
    }

    public record Prepared(Item item, WorkOrderService.ExternalPlan plan, ExternalWmWarehouse warehouse) {
    }

    private final MaterialMapper materials;
    private final UnitMeasureMapper units;
    private final RoutingMapper routings;
    private final RoutingProductMapper routes;
    private final WorkorderMapper orders;
    private final WmWarehouseMapper warehouses;
    private final WorkOrderService workorders;
    private final IWmWarehouseService warehouseService;

    public DigiwinE10SyncMapping(MaterialMapper materials, UnitMeasureMapper units, RoutingMapper routings,
                                 RoutingProductMapper routes, WorkorderMapper orders, WmWarehouseMapper warehouses,
                                 WorkOrderService workorders, IWmWarehouseService warehouseService) {
        this.materials = materials;
        this.units = units;
        this.routings = routings;
        this.routes = routes;
        this.orders = orders;
        this.warehouses = warehouses;
        this.workorders = workorders;
        this.warehouseService = warehouseService;
    }

    @Transactional(transactionManager = "businessTransactionManager", readOnly = true)
    public Prepared prepare(Kind kind, JsonNode raw, E10SyncContract.Rule rule) {
        var reasons = new ArrayList<String>();
        String code = value(raw, kind == Kind.WORKORDER ? "/doc_no" : "/warehouse_no");
        String identity = E10ExternalIdentity.resolve(raw, rule);
        var action = ExternalSyncAction.CREATE;
        if (StringUtils.isBlank(code) || code.length() > 64) {
            reasons.add("INVALID_CODE: 编码缺失或超长");
            action = ExternalSyncAction.MAPPING_REQUIRED;
        }
        if (identity == null) {
            reasons.add("IDENTITY_UNVERIFIED: 缺少经证实的稳定身份字段");
            action = ExternalSyncAction.IDENTITY_UNVERIFIED;
        }
        if (rule != null && (!Objects.equals(rule.expectedSite(), value(raw, rule.sitePointer()))
                || rule.organizationPointer() != null && !Objects.equals(rule.expectedOrganization(), value(raw, rule.organizationPointer())))) {
            reasons.add("ORGANIZATION_CONFLICT: 组织或据点与契约不一致");
            action = ExternalSyncAction.CONFLICT;
        }
        String externalStatus = Optional.ofNullable(rule)
                .map(nullableValue -> value(raw, nullableValue.statusPointer())).orElse(null);
        String targetStatus = Optional.ofNullable(externalStatus)
                .map(nullableValue -> rule.statuses().get(nullableValue)).orElse(null);
        if (targetStatus == null) {
            reasons.add("STATUS_UNVERIFIED: 未确认 ERP 业务状态转换");
            if (action == ExternalSyncAction.CREATE) action = ExternalSyncAction.STATUS_UNVERIFIED;
        }
        if (code != null && protectedCode(kind, code)) {
            reasons.add("SOURCE_CONFLICT: 同码对象为 LOCAL、历史未知来源或 CANCELLED");
            action = ExternalSyncAction.CONFLICT;
        }
        MaterialEntity product = null;
        Long routing = null;
        BigDecimal quantity = null;
        LocalDateTime start = null, end = null;
        String unit = value(raw, "/unit_no");
        if (kind == Kind.WORKORDER) {
            String productCode = value(raw, "/item_no");
            if (rule == null || rule.productKey() == null || productCode == null)
                reasons.add("MAPPING_REQUIRED: 产品编码关系尚未确认");
            else {
                var filter = new MaterialExample();
                if ("CODE".equals(rule.productKey())) filter.createCriteria().andCodeEqualTo(productCode);
                else filter.createCriteria().andExternalCodeEqualTo(productCode);
                var found = materials.selectByExample(filter);
                if (found.size() != 1 || !EnableStatus.ENABLED.getValue().equals(found.getFirst().getStatus()))
                    reasons.add("MAPPING_REQUIRED: 产品缺失、多重匹配或已禁用");
                else product = found.getFirst();
            }
            if (product != null) {
                var localUnit = Optional.ofNullable(product.getUnitId())
                        .map(nullableValue -> units.selectByPrimaryKey(nullableValue)).orElse(null);
                if (localUnit == null || !EnableStatus.ENABLED.getValue().equals(localUnit.getStatus())
                        || unit == null || !unit.equals(localUnit.getCode()))
                    reasons.add("MAPPING_REQUIRED: 单位不一致或缺少可信换算；不改变数量");
                var links = new RoutingProductExample();
                links.createCriteria().andMaterialIdEqualTo(product.getId());
                var ids = routes.selectByExample(links).stream().map(x -> x.getRoutingId()).distinct().toList();
                if (ids.isEmpty()) reasons.add("MAPPING_REQUIRED: 产品无适用路线");
                else {
                    var available = new RoutingExample();
                    available.createCriteria().andIdIn(ids).andStatusEqualTo(RoutingStatus.PUBLISHED.getValue());
                    var candidates = routings.selectByExample(available);
                    if (candidates.size() != 1) reasons.add("MAPPING_REQUIRED: 已发布适用路线不是唯一，需明确选择");
                    else routing = candidates.getFirst().getId();
                }
            }
            if (rule == null || value(raw, rule.approvalPointer()) == null || !rule.approvedValues()
                    .contains(value(raw, rule.approvalPointer())))
                reasons.add("STATUS_UNVERIFIED: 工单审核状态未通过已确认规则");
            try {
                String number = value(raw, "/plan_qty");
                quantity = Optional.ofNullable(number).map(nullableValue -> new BigDecimal(nullableValue)).orElse(null);
                if (quantity == null || quantity.signum() <= 0 || quantity.scale() > 6 || quantity.precision() - quantity.scale() > 14)
                    reasons.add("MAPPING_REQUIRED: 数量不满足 DECIMAL(20,6) 正数规则");
                start = date(value(raw, "/plan_start_date"));
                end = date(value(raw, "/plan_complete_date"));
                if (start != null && end != null && end.isBefore(start))
                    reasons.add("MAPPING_REQUIRED: 计划结束早于开始");
            } catch (NumberFormatException | DateTimeParseException invalid) {
                reasons.add("MAPPING_REQUIRED: 数量或日期格式不可信；日期只接受 ISO 日期/本地日期时间");
            }
        } else {
            String name = value(raw, "/warehouse_name");
            if (StringUtils.isBlank(name) || name.length() > 255)
                reasons.add("MAPPING_REQUIRED: 仓库名称缺失或超长");
        }
        if (reasons.isEmpty()) {
            WorkOrderService.ExternalPlan plan = null;
            ExternalWmWarehouse warehouse = null;
            ExternalSyncCheck checked;
            try {
                if (kind == Kind.WORKORDER) {
                    plan = new WorkOrderService.ExternalPlan(new WorkOrderModels.Save(code, product.getId(), quantity, routing,
                            start, end, null, null, null, null, null), identity, code, WorkOrderStatus.from(targetStatus));
                    checked = workorders.inspectExternal(plan);
                } else {
                    warehouse = new ExternalWmWarehouse(code, value(raw, "/warehouse_name"), targetStatus, null, identity);
                    checked = warehouseService.inspectExternal(warehouse);
                }
                return new Prepared(item(code, identity, checked.action(), List.of(), product, routing, quantity, unit), plan, warehouse);
            } catch (FoundationException conflict) {
                reasons.add(conflict.code().name() + ": " + conflict.code().safeMessage());
                action = ExternalSyncAction.CONFLICT;
            }
        }
        if (action == ExternalSyncAction.CREATE || action == ExternalSyncAction.STATUS_UNVERIFIED)
            action = reasons.stream().anyMatch(x -> x.startsWith("MAPPING_REQUIRED"))
                    ? ExternalSyncAction.MAPPING_REQUIRED : ExternalSyncAction.STATUS_UNVERIFIED;
        return new Prepared(item(code, identity, action, reasons, product, routing, quantity, unit), null, null);
    }

    private static Item item(String code, String identity, ExternalSyncAction action, List<String> reasons,
                             MaterialEntity product, Long routing, BigDecimal quantity, String unit) {
        return new Item(code, identity, action.getValue(), List.copyOf(reasons), Optional.ofNullable(product)
                .map(nullableValue -> nullableValue.getId()
                        .toString()).orElse(null),
                Objects.toString(routing, null), Optional.ofNullable(quantity)
                .map(nullableValue -> nullableValue.toPlainString()).orElse(null), unit);
    }

    private boolean protectedCode(Kind kind, String code) {
        if (kind == Kind.WORKORDER) {
            var f = new WorkorderExample();
            f.createCriteria().andCodeEqualTo(code);
            var found = orders.selectByExample(f);
            return found.size() > 1 || found.stream()
                    .anyMatch(row -> !BusinessSource.EXTERNAL.getValue().equals(row.getSource())
                            || WorkOrderStatus.CANCELLED.getValue().equals(row.getStatus()));
        }
        var f = new WmWarehouseExample();
        f.createCriteria().andCodeEqualTo(code);
        var found = warehouses.selectByExample(f);
        return found.size() > 1 || found.stream()
                .anyMatch(row -> !BusinessSource.EXTERNAL.getValue().equals(row.getSource()));
    }

    private static String value(JsonNode row, String pointer) {
        return E10ExternalIdentity.text(row, pointer);
    }

    private static LocalDateTime date(String text) {
        if (StringUtils.isBlank(text)) return null;
        return text.length() == 10 ? LocalDate.parse(text).atStartOfDay() : LocalDateTime.parse(text);
    }
}
