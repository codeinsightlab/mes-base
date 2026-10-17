package dev.mesbase.business.production.service;

import dev.mesbase.business.production.domain.ProductionReportModels.ExecutionSummary;
import dev.mesbase.business.production.domain.ProductionQuantityConflictException;
import dev.mesbase.business.quality.service.ProductionDefectQuantityQuery;

import dev.mesbase.business.production.dao.MesProductionExecutionMapper;
import dev.mesbase.business.production.dao.MesExecutionPathNodeMapper;
import dev.mesbase.business.production.dao.ProductionQuantityMapper;
import dev.mesbase.business.production.domain.MesExecutionPathNodeExample;
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
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.transaction.annotation.Transactional;

@Service
@ConditionalOnProperty(name = "mes.business.enabled", havingValue = "true")
@Transactional(transactionManager = "businessTransactionManager", readOnly = true)
public class ProductionQuantityService {
    private final MesProductionExecutionMapper executions;
    private final ExecutionPathService paths;
    private final MesExecutionPathNodeMapper pathNodes;
    private final ProductionQuantityMapper quantities;
    private final BusinessDirectoryService directory;
    private final ProductionDefectQuantityQuery defects;

    public ProductionQuantityService(MesProductionExecutionMapper executions, ExecutionPathService paths,
                                     MesExecutionPathNodeMapper pathNodes, ProductionQuantityMapper quantities,
                                     BusinessDirectoryService directory, ProductionDefectQuantityQuery defects) {
        this.executions = executions;
        this.paths = paths;
        this.pathNodes = pathNodes;
        this.quantities = quantities;
        this.directory = directory;
        this.defects = defects;
    }

    public ExecutionQuantity getExecutionQuantities(Long executionId) {
        return project(load(executionId));
    }

    private ExecutionQuantity load(Long executionId) {
        var execution = executions.selectByPrimaryKey(executionId);
        if (execution == null) throw new FoundationException(ErrorCode.EXECUTION_NOT_FOUND);
        var model = paths.view(executionId);
        var balance = quantities.inputBalance(executionId);
        var inputQty = balance.usableQty();
        if (model.path() == null)
            return new ExecutionQuantity(executionId, execution.getWorkorderId(), execution.getExecutionNo(),
                    execution.getRoutingId(), execution.getTargetQty(), ExecutionPathStatus.UNSELECTED.getValue(), false, "执行尚未选择实际路径", List.of(), label(model.routing()), inputQty, balance.confirmedQty(), balance.reservedQty());
        if (!Objects.equals(model.path().getRoutingId(), execution.getRoutingId())) unavailable();
        ExecutionPathValidator.validate(model.selectedNodeIds(), model.nodes(), model.edges());
        var f = new MesExecutionPathNodeExample();
        f.createCriteria().andExecutionIdEqualTo(executionId);
        f.setOrderByClause("sequence_no,id");
        var ordered = pathNodes.selectByExample(f);
        if (ordered.size() != model.selectedNodeIds().size()) unavailable();
        for (int i = 0; i < ordered.size(); i++) if (ordered.get(i).getSequenceNo() != i + 1) unavailable();
        var nodeMap = model.nodes().stream().collect(Collectors.toMap(n -> n.getId(), n -> n));
        var totals = quantities.nodeTotals(executionId).stream()
                .collect(Collectors.toMap(t -> t.routingNodeId(), t -> t.reportedQty()));
        var defectTotals = defects.nodeTotals(executionId).stream()
                .collect(Collectors.toMap(t -> t.affectedNodeId(), t -> t));
        var result = new ArrayList<NodeQuantity>();
        for (int i = 0; i < ordered.size(); i++) {
            var node = nodeMap.get(ordered.get(i).getRoutingNodeId());
            var reported = totals.getOrDefault(node.getId(), BigDecimal.ZERO);
            var quality = defectTotals.get(node.getId());
            var defect = quality == null ? BigDecimal.ZERO : quality.effectiveDefectQty();
            result.add(new NodeQuantity(executionId, node.getId(), node.getOperationId(), node.getNodeCode(), node.getOperationName() != null ? node.getOperationName() : model.operationLabels()
                    .containsKey(node.getOperationId()) ? model.operationLabels().get(node.getOperationId())
                    .name() + "（当前名称）" : null, i + 1,
                    BigDecimal.ZERO, reported, defect, BigDecimal.ZERO, BigDecimal.ZERO, true, quality == null ? BigDecimal.ZERO : quality.recoveredQty(), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, quality == null ? BigDecimal.ZERO : quality.unverifiedQty(), quality == null ? BigDecimal.ZERO : quality.directDefectQty()));
        }
        boolean locked = ExecutionPathStatus.LOCKED.getValue().equals(model.path().getPathStatus());
        return new ExecutionQuantity(executionId, execution.getWorkorderId(), execution.getExecutionNo(), execution.getRoutingId(),
                execution.getTargetQty(), model.path()
                .getPathStatus(), locked, locked ? null : "实际路径尚未锁定，禁止写入报工数量", List.copyOf(result), label(model.routing()), inputQty, balance.confirmedQty(), balance.reservedQty());
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
        return new ExecutionPage(rows, total, offset, limit, labels);
    }

    public static ExecutionQuantity project(ExecutionQuantity view) {
        return calculate(view, null, BigDecimal.ZERO, BigDecimal.ZERO);
    }

    public static void validateChange(ExecutionQuantity view, Long nodeId, BigDecimal delta) {
        if (!view.writable()) unavailable();
        var before = calculate(view, null, BigDecimal.ZERO, BigDecimal.ZERO);
        if (before.nodes().stream().anyMatch(n -> n.unverifiedDefectQty().signum() > 0))
            throw new FoundationException(ErrorCode.QUALITY_ATTRIBUTION_REQUIRED);
        var after = calculate(view, nodeId, delta, BigDecimal.ZERO);
        for (int i = 0; i < after.nodes().size(); i++) {
            var n = after.nodes().get(i);
            var old = before.nodes().get(i);
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
            result.add(new NodeQuantity(node.executionId(), node.routingNodeId(), node.operationId(), node.code(), node.name(), node.sequenceNo(), input, next, defect, output, rawAvailable.max(BigDecimal.ZERO), node.qualityIntegrated(), node.recoveredQty(), historical, rawOutput, rawAvailable, node.unverifiedDefectQty(), direct));
            input = output;
            upstream = node.routingNodeId();
        }
        if (nodeId != null && !found) unavailable();
        return new ExecutionQuantity(view.executionId(), view.workorderId(), view.executionNo(), view.routingId(), view.targetQty(), view.pathStatus(), view.writable() && result.stream()
                .noneMatch(n -> n.unverifiedDefectQty().signum() > 0), result.stream()
                .anyMatch(n -> n.unverifiedDefectQty()
                        .signum() > 0) ? "不良归属待核实，禁止新增报工及正向交接" : view.boundary(), List.copyOf(result), view.routingLabel(), view.productionInputQty(), view.confirmedInputQty(), view.reservedInputQty());
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
