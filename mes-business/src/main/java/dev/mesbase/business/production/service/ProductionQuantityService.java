package dev.mesbase.business.production.service;

import dev.mesbase.business.production.domain.ProductionReportModels.ExecutionSummary;
import dev.mesbase.business.legacy.dao.LegacyMigrationMapper;
import dev.mesbase.business.production.domain.ProductionQuantityConflictException;
import dev.mesbase.business.production.domain.ProductionQualityPendingException;
import dev.mesbase.business.quality.service.ProductionDefectQuantityQuery;

import dev.mesbase.business.production.dao.MesProductionExecutionMapper;
import dev.mesbase.business.production.dao.WorkorderMapper;
import dev.mesbase.business.production.domain.WorkOrderStatus;
import dev.mesbase.business.production.domain.ProductionExecutionStatus;
import dev.mesbase.business.production.dao.MesExecutionPathNodeMapper;
import dev.mesbase.business.production.dao.ProductionQuantityMapper;
import dev.mesbase.business.production.domain.MesExecutionPathNodeExample;
import dev.mesbase.business.production.domain.MesExecutionPathNodeEntity;
import dev.mesbase.business.production.service.ExecutionPathService.PathModel;
import dev.mesbase.business.production.service.ExecutionPathService.LockedPath;
import java.util.function.Supplier;
import dev.mesbase.business.production.service.ProductionChainLocks.LockedExecution;
import dev.mesbase.business.production.domain.MesProductionExecutionEntity;
import dev.mesbase.business.production.domain.WorkorderEntity;
import dev.mesbase.business.production.domain.WorkorderExample;
import dev.mesbase.business.production.domain.ProductionInputBalance;
import dev.mesbase.business.quality.domain.ProductionDefectModels;
import java.util.LinkedHashMap;
import dev.mesbase.business.production.domain.ExecutionPathStatus;
import dev.mesbase.business.production.domain.ProductionReportModels.ExecutionQuantity;
import dev.mesbase.business.production.domain.ProductionReportModels.ExecutionPage;
import dev.mesbase.business.production.domain.ProductionReportModels.NodeQuantity;
import dev.mesbase.core.error.ErrorCode;
import dev.mesbase.core.error.FoundationException;
import com.github.pagehelper.PageHelper;
import java.math.BigDecimal;
import java.util.List;
import dev.mesbase.business.directory.service.BusinessDirectoryService;
import dev.mesbase.business.directory.domain.DirectoryLabel;
import java.util.ArrayList;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.transaction.annotation.Transactional;

@Service
@ConditionalOnProperty(name = "mes.business.enabled", havingValue = "true")
@Transactional(transactionManager = "businessTransactionManager", readOnly = true)
public class ProductionQuantityService {
    private final MesProductionExecutionMapper executions;
    private final WorkorderMapper orders;
    private LegacyMigrationMapper history;
    private final ExecutionPathService paths;
    private final MesExecutionPathNodeMapper pathNodes;
    private final ProductionQuantityMapper quantities;
    private final BusinessDirectoryService directory;
    private final ProductionDefectQuantityQuery defects;

    public ProductionQuantityService(MesProductionExecutionMapper executions, ExecutionPathService paths,
                                     MesExecutionPathNodeMapper pathNodes, ProductionQuantityMapper quantities,
                                     BusinessDirectoryService directory, ProductionDefectQuantityQuery defects,
                                     WorkorderMapper orders) {
        this.orders = orders;
        this.executions = executions;
        this.paths = paths;
        this.pathNodes = pathNodes;
        this.quantities = quantities;
        this.directory = directory;
        this.defects = defects;
    }

    @Autowired
    public ProductionQuantityService(MesProductionExecutionMapper executions, ExecutionPathService paths,
                                     MesExecutionPathNodeMapper pathNodes, ProductionQuantityMapper quantities,
                                     BusinessDirectoryService directory, ProductionDefectQuantityQuery defects,
                                     WorkorderMapper orders, ObjectProvider<LegacyMigrationMapper> history) {
        this(executions, paths, pathNodes, quantities, directory, defects, orders);
        this.history = history.getIfAvailable();
    }

    public ExecutionQuantity getExecutionQuantities(Long executionId) {
        return project(load(executionId));
    }

    /**
     * Bounded set reads followed by in-memory grouping; arithmetic is shared with the single-execution path.
     */
    public Map<Long, ExecutionQuantity> getHistoricalExecutionQuantities(List<Long> executionIds) {
        var batch = paths.viewBatch(executionIds);
        if (batch.executions().isEmpty()) return Map.of();
        if (history == null || batch.executions().values().stream()
                .anyMatch(e -> !ProductionExecutionStatus.HISTORICAL.getValue().equals(e.getStatus()))) unavailable();
        var ids = List.copyOf(batch.executions().keySet());
        var inputs = history.inputs(ids).stream().collect(Collectors.toMap(n -> n.executionId(), n -> n.quantity()));
        var nodes = history.nodesBatch(ids).stream().collect(Collectors.groupingBy(n -> n.executionId(),
                Collectors.toMap(n -> n.routingNodeId(), n -> new HistoricalNode(n.legacyNet(), n.grossCorrection(), n.effectiveDefect()))));
        var balances = quantities.inputBalances(ids).stream().collect(Collectors.toMap(n -> n.executionId(),
                n -> new ProductionInputBalance(n.confirmedQty(), n.reservedQty())));
        var totals = quantities.nodeTotalsBatch(ids).stream().collect(Collectors.groupingBy(n -> n.executionId(),
                Collectors.toMap(n -> n.routingNodeId(), n -> n.reportedQty())));
        var quality = defects.nodeTotalsBatch(ids);
        var workorderIds = batch.executions().values().stream().map(MesProductionExecutionEntity::getWorkorderId)
                .distinct().toList();
        var filter = new WorkorderExample();
        filter.createCriteria().andIdIn(workorderIds);
        var workorders = orders.selectByExample(filter).stream()
                .collect(Collectors.toMap(WorkorderEntity::getId, v -> v));
        var result = new LinkedHashMap<Long, ExecutionQuantity>();
        for (Long id : ids) {
            var e = batch.executions().get(id);
            var model = batch.models().get(id);
            var balance = balances.getOrDefault(id, new ProductionInputBalance(BigDecimal.ZERO, BigDecimal.ZERO));
            var input = inputs.get(id);
            if (input == null || input.signum() < 0) unavailable();
            if (model.path() == null) {
                result.put(id, new ExecutionQuantity(id, e.getWorkorderId(), e.getExecutionNo(), e.getRoutingId(),
                        e.getTargetQty(), ExecutionPathStatus.UNSELECTED.getValue(), false, "执行尚未选择实际路径",
                        List.of(), label(model.routing()), input, balance.confirmedQty(), balance.reservedQty()));
                continue;
            }
            if (!Objects.equals(model.path().getRoutingId(), e.getRoutingId())) unavailable();
            ExecutionPathValidator.validate(model.selectedNodeIds(), model.nodes(), model.edges());
            var ordered = validateOrdered(batch.ordered().getOrDefault(id, List.of()), model);
            result.put(id, project(compose(e, model, new QuantityFacts(balance, input, true,
                    nodes.getOrDefault(id, Map.of()), ordered, totals.getOrDefault(id, Map.of()),
                    quality.getOrDefault(id, List.of()).stream()
                            .collect(Collectors.toMap(n -> n.affectedNodeId(), n -> n)),
                    workorders.get(e.getWorkorderId())))));
        }
        return Map.copyOf(result);
    }

    /**
     * Historical ledger access validates the actual path without calculating mutable production quantities.
     */
    public void requireReadableExecutionNode(Long executionId, Long nodeId) {
        var model = paths.view(executionId);
        if (model.path() == null || !Objects.equals(model.path().getRoutingId(), model.routing().id())) unavailable();
        ExecutionPathValidator.validate(model.selectedNodeIds(), model.nodes(), model.edges());
        if (orderedPathNodes(executionId, model).stream()
                .noneMatch(row -> Objects.equals(row.getRoutingNodeId(), nodeId)))
            unavailable();
    }

    private List<MesExecutionPathNodeEntity> orderedPathNodes(Long executionId, PathModel model) {
        var f = new MesExecutionPathNodeExample();
        f.createCriteria().andExecutionIdEqualTo(executionId);
        f.setOrderByClause("sequence_no,id");
        return validateOrdered(pathNodes.selectByExample(f), model);
    }

    private List<MesExecutionPathNodeEntity> validateOrdered(List<MesExecutionPathNodeEntity> ordered,
                                                             PathModel model) {
        if (ordered.size() != model.selectedNodeIds().size()) unavailable();
        for (int i = 0; i < ordered.size(); i++)
            if (ordered.get(i).getSequenceNo() != i + 1
                    || !Objects.equals(ordered.get(i).getRoutingNodeId(), model.selectedNodeIds().get(i)))
                unavailable();
        return ordered;
    }

    private ExecutionQuantity load(Long executionId) {
        var execution = executions.selectByPrimaryKey(executionId);
        if (execution == null) throw new FoundationException(ErrorCode.EXECUTION_NOT_FOUND);
        return load(executionId, execution, paths.view(executionId));
    }

    private ExecutionQuantity load(Long executionId, MesProductionExecutionEntity execution, PathModel model) {
        return load(executionId, execution, model, () -> orderedPathNodes(executionId, model));
    }

    private ExecutionQuantity load(Long executionId, MesProductionExecutionEntity execution, PathModel model,
                                   Supplier<List<MesExecutionPathNodeEntity>> selectedRows) {
        var balance = quantities.inputBalance(executionId);
        boolean historical = ProductionExecutionStatus.HISTORICAL.getValue().equals(execution.getStatus());
        if (historical && history == null) unavailable();
        var inputQty = historical ? history.input(executionId) : balance.usableQty();
        if (inputQty == null || inputQty.signum() < 0) unavailable();
        var historyNodes = historical ? history.nodes(executionId).stream()
                .collect(Collectors.toMap(n -> n.routingNodeId(), n -> new HistoricalNode(n.legacyNet(), n.grossCorrection(), n.effectiveDefect()))) : null;
        if (model.path() == null)
            return new ExecutionQuantity(executionId, execution.getWorkorderId(), execution.getExecutionNo(),
                    execution.getRoutingId(), execution.getTargetQty(), ExecutionPathStatus.UNSELECTED.getValue(), false, "执行尚未选择实际路径", List.of(), label(model.routing()), inputQty, balance.confirmedQty(), balance.reservedQty());
        if (!Objects.equals(model.path().getRoutingId(), execution.getRoutingId())) unavailable();
        ExecutionPathValidator.validate(model.selectedNodeIds(), model.nodes(), model.edges());
        var ordered = selectedRows.get();
        var totals = quantities.nodeTotals(executionId).stream()
                .collect(Collectors.toMap(t -> t.routingNodeId(), t -> t.reportedQty()));
        var defectTotals = defects.nodeTotals(executionId).stream()
                .collect(Collectors.toMap(t -> t.affectedNodeId(), t -> t));
        return compose(execution, model, new QuantityFacts(balance, inputQty, historical, historyNodes, ordered,
                totals, defectTotals, orders.selectByPrimaryKey(execution.getWorkorderId())));
    }

    private record HistoricalNode(BigDecimal legacyNet, BigDecimal grossCorrection, BigDecimal effectiveDefect) {
    }

    private record QuantityFacts(ProductionInputBalance balance, BigDecimal inputQty, boolean historical,
                                 Map<Long, HistoricalNode> historyNodes, List<MesExecutionPathNodeEntity> ordered,
                                 Map<Long, BigDecimal> totals, Map<Long, ProductionDefectModels.NodeTotal> defectTotals,
                                 WorkorderEntity order) {
    }

    private ExecutionQuantity compose(MesProductionExecutionEntity execution, PathModel model, QuantityFacts facts) {
        Long executionId = execution.getId();
        var balance = facts.balance();
        var inputQty = facts.inputQty();
        boolean historical = facts.historical();
        var historyNodes = facts.historyNodes();
        var ordered = facts.ordered();
        var totals = facts.totals();
        var defectTotals = facts.defectTotals();
        var order = facts.order();
        var nodeMap = model.nodes().stream().collect(Collectors.toMap(n -> n.getId(), n -> n));
        var result = new ArrayList<NodeQuantity>();
        for (int i = 0; i < ordered.size(); i++) {
            var node = nodeMap.get(ordered.get(i).getRoutingNodeId());
            var reported = totals.getOrDefault(node.getId(), BigDecimal.ZERO);
            var quality = defectTotals.get(node.getId());
            var defect = quality == null ? BigDecimal.ZERO : quality.effectiveDefectQty();
            if (historical) {
                var evidence = historyNodes.get(node.getId());
                if (evidence == null || evidence.legacyNet().compareTo(reported) != 0 || quality != null)
                    unavailable();
                reported = reported.add(evidence.grossCorrection());
                defect = evidence.effectiveDefect();
            }
            result.add(new NodeQuantity(executionId, node.getId(), node.getOperationId(), node.getNodeCode(), node.getOperationName() != null ? node.getOperationName() : model.operationLabels()
                    .containsKey(node.getOperationId()) ? model.operationLabels().get(node.getOperationId())
                    .name() + "（当前名称）" : null, i + 1,
                    BigDecimal.ZERO, reported, defect, BigDecimal.ZERO, BigDecimal.ZERO, true, quality == null ? BigDecimal.ZERO : quality.recoveredQty(), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, quality == null ? BigDecimal.ZERO : quality.unverifiedQty(), historical ? defect : quality == null ? BigDecimal.ZERO : quality.directDefectQty()));
        }
        boolean locked = ExecutionPathStatus.LOCKED.getValue().equals(model.path().getPathStatus());
        String boundary = historical ? "历史只读；投入为冻结材料证据推导，禁止正常生产操作" : !locked ? "实际路径尚未锁定，禁止写入报工数量"
                : order == null || !WorkOrderStatus.RELEASED.getValue().equals(order.getStatus())
                ? "工单未下达或已作废，禁止继续正常报工"
                : !ProductionExecutionStatus.allowsNormalReporting(execution.getStatus())
                ? "执行已关闭或状态非法，禁止继续正常报工" : null;
        // writable describes normal forward reporting only, not historical correction authorization.
        return new ExecutionQuantity(executionId, execution.getWorkorderId(), execution.getExecutionNo(), execution.getRoutingId(),
                execution.getTargetQty(), model.path()
                .getPathStatus(), boundary == null, boundary, List.copyOf(result), label(model.routing()), inputQty, balance.confirmedQty(), balance.reservedQty(), historical);
    }

    private static String label(DirectoryLabel label) {
        return label == null ? null : label.code() + " / V" + label.version() + " · " + label.name();
    }

    public List<NodeQuantity> getNodeQuantities(Long executionId) {
        return getExecutionQuantities(executionId).nodes();
    }

    public NodeQuantity getNodeQuantity(Long executionId, Long nodeId) {
        return getNodeQuantities(executionId).stream().filter(n -> n.routingNodeId().equals(nodeId)).findFirst()
                .orElseThrow(() -> new FoundationException(ErrorCode.REPORT_PATH_REQUIRED));
    }

    public ExecutionPage getWorkorderQuantities(Long workorderId, int offset, int limit) {
        paging(offset, limit);
        List<ExecutionSummary> rows;
        long total;
        try (var page = PageHelper.offsetPage(offset, limit)) {
            rows = List.copyOf(quantities.workorderQuantities(workorderId));
            total = page.getTotal();
        }
        var labels = directory.routingLabels(rows.stream().map(ExecutionSummary::routingId).toList()).entrySet()
                .stream().collect(Collectors.toMap(entry -> entry.getKey(), entry -> label(entry.getValue())));
        var order = orders.selectByPrimaryKey(workorderId);
        if (order != null && WorkOrderStatus.HISTORICAL.getValue().equals(order.getStatus()) && !rows.isEmpty()) {
            if (history == null) unavailable();
            var historicalInputs = history.inputs(rows.stream().map(ExecutionSummary::executionId).toList()).stream()
                    .collect(Collectors.toMap(v -> v.executionId(), v -> v.quantity()));
            if (historicalInputs.size() != rows.size()) unavailable();
            rows = rows.stream().map(v -> new ExecutionSummary(v.executionId(), v.workorderId(), v.executionNo(),
                    v.routingId(), v.targetQty(), v.pathStatus(), v.lastNodeReportedQty(),
                    historicalInputs.get(v.executionId()))).toList();
        }
        return new ExecutionPage(rows, total, offset, limit, labels);
    }

    public static ExecutionQuantity project(ExecutionQuantity view) {
        return calculate(view, null, BigDecimal.ZERO, BigDecimal.ZERO);
    }

    public static void validateChange(ExecutionQuantity view, Long nodeId, BigDecimal delta) {
        // Historical corrections retain their formal gates even after normal reporting closes.
        if (view.historical()) unavailable();
        if (!ExecutionPathStatus.LOCKED.getValue().equals(view.pathStatus())) unavailable();
        var before = calculate(view, null, BigDecimal.ZERO, BigDecimal.ZERO);
        String blocked = positiveReportBlockReason(before, nodeId);
        if (delta.signum() > 0 && blocked != null) throw new ProductionQualityPendingException(blocked);
        var after = calculate(view, nodeId, delta, BigDecimal.ZERO);
        for (int i = 0; i < after.nodes().size(); i++) {
            var n = after.nodes().get(i);
            var old = before.nodes().get(i);
            if (delta.signum() < 0 && n.routingNodeId().equals(nodeId)
                    && n.reportedQty().compareTo(n.unverifiedDefectQty()) < 0)
                throw new ProductionQualityPendingException("工序 " + n.code() + " · " + n.name()
                        + " 订正后报工不足以解释待核实不良申报，请先核实质量记录。");
            if (n.historicalOverrunQty().compareTo(old.historicalOverrunQty()) > 0 || n.routingNodeId()
                    .equals(nodeId) && delta.signum() > 0 && n.reportedQty().compareTo(n.inputQty()) > 0)
                throw new ProductionQuantityConflictException(n.routingNodeId(), i == 0 ? null : after.nodes()
                        .get(i - 1).routingNodeId(), n.inputQty(), n.reportedQty(), n.netDefectQty());
        }
    }

    /**
     * Caller must hold the common WorkOrder -> Execution locks in its write transaction.
     */
    public void validateReportChange(Long executionId, Long nodeId, BigDecimal delta) {
        validateChange(load(executionId), nodeId, delta);
    }

    public LockedPath validateReportChange(LockedExecution locked, Long nodeId, BigDecimal delta) {
        var path = paths.lockedPath(locked);
        var execution = locked.execution();
        validateChange(load(execution.getId(), execution, path.model(), path::orderedRows), nodeId, delta);
        return path;
    }

    /**
     * Quality may exist before path locking; only report writes require LOCKED.
     */
    public void validateDefectChange(Long executionId, Long nodeId, BigDecimal contributionDelta) {
        calculate(load(executionId), nodeId, BigDecimal.ZERO, contributionDelta);
    }

    private static ExecutionQuantity calculate(ExecutionQuantity view, Long nodeId, BigDecimal reportDelta,
                                               BigDecimal defectDelta) {
        boolean found = false;
        var input = view.productionInputQty();
        Long upstream = null;
        var result = new ArrayList<NodeQuantity>();
        for (var node : view.nodes()) {
            var next = node.reportedQty();
            var defect = node.defectQty();
            var direct = node.directDefectQty();
            if (node.routingNodeId().equals(nodeId)) {
                next = next.add(reportDelta);
                defect = defect.add(defectDelta);
                direct = direct.add(defectDelta);
                found = true;
            }
            var netDefect = direct.subtract(node.recoveredQty());
            if (next.signum() < 0 || defect.signum() < 0 || direct.signum() < 0 || node.recoveredQty()
                    .signum() < 0 || netDefect.signum() < 0 || next.compareTo(direct) < 0)
                throw new ProductionQuantityConflictException(node.routingNodeId(), upstream, input, next, netDefect);
            var rawAvailable = input.subtract(next);
            var historical = rawAvailable.negate().max(BigDecimal.ZERO);
            var rawOutput = next.subtract(netDefect).subtract(historical);
            var output = rawOutput.max(BigDecimal.ZERO);
            result.add(new NodeQuantity(node.executionId(), node.routingNodeId(), node.operationId(), node.code(), node.name(), node.sequenceNo(), input, next, defect, output, view.historical() ? BigDecimal.ZERO : rawAvailable.max(BigDecimal.ZERO), node.qualityIntegrated(), node.recoveredQty(), historical, rawOutput, rawAvailable, node.unverifiedDefectQty(), direct));
            input = output;
            upstream = node.routingNodeId();
        }
        if (nodeId != null && !found) unavailable();
        return new ExecutionQuantity(view.executionId(), view.workorderId(), view.executionNo(), view.routingId(), view.targetQty(), view.pathStatus(), view.writable(), result.stream()
                .anyMatch(n -> n.unverifiedDefectQty()
                        .signum() > 0) ? "存在待核实不良；从受影响工序起禁止新增正向报工，请核实质量记录，正向交接保持质量门禁" : view.boundary(), List.copyOf(result), view.routingLabel(), view.productionInputQty(), view.confirmedInputQty(), view.reservedInputQty(), view.historical());
    }

    /**
     * Shared by positive submit validation, candidate queries and administrator UI projection.
     */
    public static String positiveReportBlockReason(ExecutionQuantity view, Long nodeId) {
        if (view.historical()) return "历史只读，不能继续报工";
        if (!ExecutionPathStatus.LOCKED.getValue().equals(view.pathStatus())) return "实际路径尚未锁定";
        NodeQuantity affected = null;
        for (var n : view.nodes()) {
            if (affected == null && n.unverifiedDefectQty().signum() > 0) affected = n;
            if (n.routingNodeId().equals(nodeId)) return affected == null ? null :
                    "工序 " + affected.code() + " · " + affected.name() + " 存在待核实不良 "
                            + affected.unverifiedDefectQty()
                            .toPlainString() + "；当前工序受其影响，须先核实质量记录再新增报工。";
        }
        throw new FoundationException(ErrorCode.REPORT_PATH_REQUIRED);
    }

    public void validateInputReduction(Long executionId) {
        var view = getExecutionQuantities(executionId);
        if (view.nodes().stream().anyMatch(n -> n.historicalConflict() || n.unverifiedDefectQty().signum() > 0))
            throw new FoundationException(ErrorCode.REPORT_QUANTITY_CONFLICT);
    }

    public static void paging(int offset, int limit) {
        if (offset < 0 || limit < 1 || limit > 100) throw new FoundationException(ErrorCode.BAD_REQUEST);
    }

    private static void unavailable() {
        throw new FoundationException(ErrorCode.REPORT_PATH_REQUIRED);
    }
}
